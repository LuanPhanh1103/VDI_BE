package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.AnsibleJobMessageRequest;
import com.mobifone.vdi.entity.AnsibleJob;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.repository.AnsibleJobRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnsibleRunnerService {

    final AnsibleJobRepository jobRepository;

    // Tiến trình SSH local theo jobId để có thể cancel
    final ConcurrentMap<String, Process> RUNNING = new ConcurrentHashMap<>();

    // ====== Config từ application.yml (có default) ======
    @NonFinal
    @Value("${ansible.max-retries:1}")
    protected int maxRetries;

    @NonFinal
    @Value("${ansible.timeout-minutes:30}")
    protected long timeoutMinutes;

    @NonFinal
    @Value("${ansible.retry-backoff-ms:10000}")
    protected long retryBackoffMs;

    @NonFinal
    @Value("${ansible.remote.host:192.168.220.133}")
    protected String remoteHost;

    @NonFinal
    @Value("${ansible.remote.user:root}")
    protected String remoteUser;

    @NonFinal
    @Value("${ansible.remote.jobs-dir:/ansible-host/jobs}")
    protected String remoteJobsDir;

    @NonFinal
    @Value("${ansible.remote.logs-dir:/ansible-host/logs}")
    protected String remoteLogsDir;

    @NonFinal
    @Value("${ansible.remote.roles-dir:/ansible-host/roles}")
    protected String remoteRolesDir;

    // ===================== PUBLIC API =====================

    @Async
    public void runAnsibleJob(AnsibleJobMessageRequest jobRequest) {
        final String jobId = jobRequest.getJobId();
        final String logFile = remoteLogsDir + "/" + jobId + ".log";

        AnsibleJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_JOB_NOT_FOUND));
        job.setStatus("RUNNING");
        job.setLogPath(logFile);
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        // Scheduler cho retry (không BusyWait)
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ansible-retry-" + jobId);
            t.setDaemon(true);
            return t;
        });

        AtomicInteger attempt = new AtomicInteger(0);

        Runnable attemptTask = new Runnable() {
            @Override
            public void run() {
                int nth = attempt.incrementAndGet();
                try {
                    appendRemoteLog(jobId, String.format("===== ATTEMPT #%d START =====", nth));
                    log.info("🔄 Job {} – attempt {}/{}", jobId, nth, maxRetries + 1);

                    boolean ok = runOnce(jobRequest, nth); // chạy 1 lần; true nếu SUCCESS

                    if (ok) {
                        appendRemoteLog(jobId, String.format("===== ATTEMPT #%d RESULT: SUCCESS =====", nth));
                        updateStatus(jobId, "SUCCESS");
                        scheduler.shutdown();
                        return;
                    }

                    if (nth <= maxRetries) {
                        long delay = retryBackoffMs * nth; // backoff tăng dần
                        appendRemoteLog(jobId, String.format("===== ATTEMPT #%d RESULT: FAILED → RETRY in %d ms =====", nth, delay));
                        log.warn("⏳ Job {} failed attempt {}/{}. Retry in {} ms", jobId, nth, maxRetries + 1, delay);
                        scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
                    } else {
                        appendRemoteLog(jobId, String.format("===== ATTEMPT #%d RESULT: FAILED (NO MORE RETRIES) =====", nth));
                        updateStatus(jobId, "FAILED");
                        scheduler.shutdown();
                    }
                } catch (Exception ex) {
                    appendRemoteLog(jobId, String.format("===== ATTEMPT #%d EXCEPTION → %s =====", nth, ex.getClass().getSimpleName()));
                    log.error("❌ Job {} error at attempt {}/{}", jobId, nth, maxRetries + 1, ex);
                    if (nth <= maxRetries) {
                        long delay = retryBackoffMs * nth;
                        scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
                    } else {
                        updateStatus(jobId, "FAILED");
                        scheduler.shutdown();
                    }
                }
            }
        };

        // chạy lần đầu ngay lập tức
        scheduler.schedule(attemptTask, 0, TimeUnit.MILLISECONDS);
    }

    public void cancelJob(String jobId) {
        AnsibleJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_JOB_NOT_FOUND));

        job.setStatus("CANCEL_REQUESTED");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        appendRemoteLog(jobId, "===== CANCEL REQUESTED → killing remote processes =====");

        // kill SSH local
        Process p = RUNNING.remove(jobId);
        if (p != null) {
            try { p.destroyForcibly(); } catch (Exception ignored) {}
        }

        // kill từ xa
        killRemote(jobId);

        appendRemoteLog(jobId, "===== CANCELLED =====");
        job.setStatus("CANCELLED");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    public AnsibleJob getJob(String jobId) {
        return jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_JOB_NOT_FOUND));
    }

    public String getJobLog(String jobId) {
        AnsibleJob job = getJob(jobId);
        try {
            String path = job.getLogPath();
            if (path == null || path.isEmpty()) throw new AppException(ErrorCode.LOG_FILE_PATH);

            String command = String.format(
                    "ssh -o StrictHostKeyChecking=no %s@%s cat %s",
                    remoteUser, remoteHost, path
            );
            ProcessBuilder pb = new ProcessBuilder(detectShell(), detectShellFlag(), command);
            Process process = pb.start();
            byte[] out = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit != 0) throw new AppException(ErrorCode.LOG_FILE_READ);

            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException(ErrorCode.LOG_FILE_READ);
        }
    }

    // ===================== CORE EXECUTION =====================

    /** Chạy 1 lần ansible cho job, trả true nếu SUCCESS */
    private boolean runOnce(AnsibleJobMessageRequest req, int attemptNo) throws Exception {
        final String jobId = req.getJobId();
        final String inventoryFileName = "inventory_" + jobId + ".ini";
        final String playbookFileName  = "playbook_" + jobId + ".yml";
        final String logFile = remoteLogsDir + "/" + jobId + ".log";

//        // 1) Sinh inventory
//        StringBuilder inventory = new StringBuilder();
//        inventory.append("[windows]\n");
//        for (String ip : req.getTargetIps()) inventory.append(ip).append("\n");
//        inventory.append("\n[windows:vars]\n")
//                .append("ansible_user=Administrator\n")
//                .append("ansible_password=Mbf@phanh2025#\n") // TODO: move to secure config/secret
//                .append("ansible_port=5985\n")
//                .append("ansible_connection=winrm\n")
//                .append("ansible_winrm_transport=basic\n")
//                .append("ansible_winrm_server_cert_validation=ignore\n");
        // Lấy giá trị từ request, có default
        final String ansibleUser = (req.getAnsibleUser() != null && !req.getAnsibleUser().isBlank())
                ? req.getAnsibleUser()
                : "Administrator";
        final int ansiblePort = (req.getAnsiblePort() != null)
                ? req.getAnsiblePort()
                : 5985;

        // 1) Sinh inventory
                StringBuilder inventory = new StringBuilder();
                inventory.append("[windows]\n");
                for (String ip : req.getTargetIps()) inventory.append(ip).append("\n");
                inventory.append("\n[windows:vars]\n")
                        .append("ansible_user=").append(ansibleUser).append("\n")
                        .append("ansible_password=Mbf@phanh2025#\n") // TODO: move to secure config/secret
                        .append("ansible_port=").append(ansiblePort).append("\n")
                        .append("ansible_connection=winrm\n")
                        .append("ansible_winrm_transport=basic\n")
                        .append("ansible_winrm_server_cert_validation=ignore\n");

        // 2) Playbook – nhúng extraVars vào block role
        Map<String, Object> extraVars = Optional.ofNullable(req.getExtraVars()).orElse(new HashMap<>());
        String rolesYaml = req.getApps().stream()
                .map(app -> {
                    StringBuilder b = new StringBuilder("  - role: " + app + "\n");
                    b.append("    win_version: ").append(req.getWinVersion()).append("\n");
                    for (Map.Entry<String, Object> e : extraVars.entrySet()) {
                        b.append("    ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                    }
                    return b.toString();
                })
                .collect(Collectors.joining("\n"));

        String playbook =
                "- hosts: windows\n" +
                        "  gather_facts: yes\n" +
                        "  roles:\n" +
                        rolesYaml + "\n";

        // 3) Upload file
        writeRemoteFile(remoteJobsDir + "/" + inventoryFileName, inventory.toString());
        writeRemoteFile(remoteJobsDir + "/" + playbookFileName,  playbook);

        // 4) Tạo thư mục từ xa (jobs/logs/pids)
        execLocal(String.format(
                "ssh %1$s@%2$s \"mkdir -p %3$s %4$s /ansible-host/pids\"",
                remoteUser, remoteHost, remoteJobsDir, remoteLogsDir
        ));

        // 5) Chạy ansible-playbook, export ROLES_PATH & ghi ssh pid, APPEND log + marker END
        String command = String.format(
                "ssh %1$s@%2$s \"export ANSIBLE_ROLES_PATH='%3$s'; " +
                        "echo $$ > /ansible-host/pids/%4$s.sshpid; " +
                        "echo '===== ATTEMPT #%5$d START ansible-playbook =====' >> %8$s; " +
                        "{ ansible-playbook -i %6$s/%7$s %6$s/%9$s >> %8$s 2>&1; rc=$?; } ; " +
                        "echo '===== ATTEMPT #%5$d END (exit='$rc') =====' >> %8$s; " +
                        "exit $rc\"",
                remoteUser, remoteHost, remoteRolesDir, jobId,
                attemptNo, remoteJobsDir, inventoryFileName, logFile, playbookFileName
        );


        ProcessBuilder pb = new ProcessBuilder(detectShell(), detectShellFlag(), command);
        Process process = pb.start();
        RUNNING.put(jobId, process);

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            appendRemoteLog(jobId, "===== TIMEOUT → killing remote processes =====");
            log.warn("Job {} timeout sau {} phút", jobId, timeoutMinutes);
            killRemote(jobId);
            try { process.destroyForcibly(); } catch (Exception ignored) {}
            RUNNING.remove(jobId);
            return false;
        }

        int exit = process.exitValue();
        RUNNING.remove(jobId);
        return exit == 0;
    }

    // ===================== UTILITIES =====================

    private void updateStatus(String jobId, String status) {
        AnsibleJob job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_JOB_NOT_FOUND));
        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private void killRemote(String jobId) {
        try {
            // placeholders: %1$s remoteUser, %2$s remoteHost, %3$s jobId
            String kill = String.format(
                    "ssh %1$s@%2$s \"(test -f /ansible-host/pids/%3$s.pid && kill -9 \\$(cat /ansible-host/pids/%3$s.pid) 2>/dev/null || true); " +
                            "(test -f /ansible-host/pids/%3$s.sshpid && kill -9 \\$(cat /ansible-host/pids/%3$s.sshpid) 2>/dev/null || true); " +
                            "pkill -f playbook_%3$s.yml || true\"",
                    remoteUser, remoteHost, jobId
            );
            execLocal(kill);
        } catch (Exception e) {
            log.warn("killRemote failed for job {}", jobId, e);
        }
    }

    private String detectShell() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "cmd.exe" : "bash";
    }

    private String detectShellFlag() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "/c" : "-lc"; // -lc để bash hiểu export/pipe
    }

    private void execLocal(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(detectShell(), detectShellFlag(), command);
        Process proc = pb.start();
        proc.waitFor();
    }

    private void writeRemoteFile(String remotePath, String content) throws Exception {
        String tmp = Files.createTempFile("ansible_", ".tmp").toString();
        // chuẩn hoá path khi đang chạy trên Windows cho scp
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            tmp = tmp.replace("\\", "/");
        }
        try (FileWriter fw = new FileWriter(tmp, StandardCharsets.UTF_8)) {
            fw.write(content);
        }
        String scpCmd = String.format("scp \"%s\" %s@%s:%s", tmp, remoteUser, remoteHost, remotePath);
        execLocal(scpCmd);

        // xoá file tạm local
        Files.deleteIfExists(Paths.get(tmp.replace("/", FileSystems.getDefault().getSeparator())));
    }

    /** Append 1 dòng message vào log từ xa theo thời gian thực (giữ log cũ + thêm marker) */
    private void appendRemoteLog(String jobId, String message) {
        try {
            String logFile = remoteLogsDir + "/" + jobId + ".log";
            String cmd = String.format(
                    "ssh %1$s@%2$s \"echo '[%3$s] %4$s' >> %5$s\"",
                    remoteUser, remoteHost,
                    java.time.ZonedDateTime.now(),
                    message.replace("\"","\\\""),
                    logFile
            );
            execLocal(cmd);
        } catch (Exception e) {
            log.warn("appendRemoteLog failed for job {}", jobId, e);
        }
    }
}
