package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.AnsibleJobMessageRequest;
import com.mobifone.vdi.entity.AnsibleJob;
import com.mobifone.vdi.entity.AppDefinition;
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
    @Value("${ansible.remote.host:42.1.124.196}")
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
    @Value("${ansible.remote.roles-dir:/ansible-host/window/roles}")
    protected String remoteRolesDir;

    @NonFinal
    @Value("${ansible.remote.port:2223}")
    protected int remotePort;

    // ✅ THÊM: inventory pfSense đúng chỗ bạn đang để
    @NonFinal @Value("${ansible.pfsense.inventory:/ansible-host/pfsense2.8/pfsense.ini}")
    protected String pfsenseInventory;

    // ✅ THÊM: tuỳ chọn ssh để không bị hỏi host key + không lưu known_hosts
    private static final String SSH_OPTS = "-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null";

    /** NAT forward: dùng đúng inventory pfSense + đảm bảo mkdir logs trước khi ghi */
    public boolean runNatCreate(String jobId, String wanIp, int destPort, String localIp, int localPort) {
        String logFile = remoteLogsDir + "/" + jobId + "_nat.log";
        String cmd = String.format(
                "ssh %s -p %d %s@%s \"mkdir -p %s; " +
                        "echo '===== NAT START =====' >> %s; " +
                        "{ ansible-playbook /ansible-host/pfsense2.8/create_nat.yml -i %s " +
                        "-e \\\"wan_ip=%s destination_port=%d target=%s localip=%s local_port=%d\\\" >> %s 2>&1; rc=$?; } ; " +
                        "echo '===== NAT END (exit='$rc') =====' >> %s; exit $rc\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteLogsDir,
                logFile,
                pfsenseInventory,
                wanIp, destPort, localIp, localIp, localPort,
                logFile, logFile
        );
        return execAndWait(jobId, cmd, logFile);
    }

    // ✅ THÊM: NAT delete (delete-nat.yml -e "wan_ip=... port=...")
    public boolean runNatDelete(String jobId, String wanIp, int destPort) {
        String logFile = remoteLogsDir + "/" + jobId + "_nat_delete.log";
        String cmd = String.format(
                "ssh %s -p %d %s@%s \"mkdir -p %s; " +
                        "echo '===== NAT DELETE START =====' >> %s; " +
                        "{ ansible-playbook /ansible-host/pfsense2.8/delete-nat.yml -i %s " +
                        "-e \\\"wan_ip=%s port=%d\\\" >> %s 2>&1; rc=$?; } ; " +
                        "echo '===== NAT DELETE END (exit='$rc') =====' >> %s; exit $rc\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteLogsDir,
                logFile,
                pfsenseInventory,
                wanIp, destPort,
                logFile, logFile
        );
        return execAndWait(jobId, cmd, logFile);
    }

    /** Assign interface cho ORGANIZATION: dùng đúng inventory pfSense + mkdir logs */
    public boolean runAssignInterface(String jobId, String assignName, String assignType, String assignDescr,
                                      String assignIp, int assignMask) {
        String logFile = remoteLogsDir + "/" + jobId + "_assign_interface.log";
        String cmd = String.format(
                "ssh %s -p %d %s@%s \"mkdir -p %s; " +
                        "echo '===== ASSIGN START =====' >> %s; " +
                        "{ ansible-playbook /ansible-host/pfsense2.8/enable-assign-interface.yml -i %s " +
                        "-e \\\"assign_name=%s assign_type=%s assign_descr=%s assign_ip=%s assign_mask=%d\\\" >> %s 2>&1; rc=$?; } ; " +
                        "echo '===== ASSIGN END (exit='$rc') =====' >> %s; exit $rc\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteLogsDir,
                logFile,
                pfsenseInventory,
                assignName, assignType, assignDescr, assignIp, assignMask,
                logFile, logFile
        );

        return execAndWait(jobId, cmd, logFile);
    }


    public boolean runWinRmDisable(String jobId, String ip, String port, String user, String pass, String winVersion) {
        String logFile = remoteLogsDir + "/" + jobId + "_winrm_disable.log";

        // inventory sử dụng ipPublic & portWinrmPublic
        String inv = "[windows]\n" + ip + "\n\n[windows:vars]\n" +
                "ansible_user=" + user + "\n" +
                "ansible_password=" + pass + "\n" +
                "ansible_port=" + port + "\n" +
                "ansible_connection=winrm\n" +
                "ansible_winrm_scheme=http\n" +
                "ansible_winrm_transport=basic\n" +
                "ansible_winrm_server_cert_validation=ignore\n";
        String invPath = remoteJobsDir + "/inventory_" + jobId + "_disable.ini";
        try { writeRemoteFile(invPath, inv); } catch (Exception e) { return false; }

        String pbPath = remoteJobsDir + "/playbook_" + jobId + "_winrm_disable.yml";
        String play =
                "- hosts: windows\n" +
                        "  gather_facts: yes\n" +
                        "  roles:\n" +
                        "    - role: winrm_disable\n" +
                        "      win_version: " + yamlScalar(winVersion) + "\n" +
                        "      winrm_action: remove\n";
        try { writeRemoteFile(pbPath, play); } catch (Exception e) { return false; }

        String cmd = String.format(
                "ssh %s -p %d %s@%s \"export ANSIBLE_ROLES_PATH='%s'; " +
                        "echo '===== WINRM DISABLE START =====' >> %s; " +
                        "{ ansible-playbook -i %s %s >> %s 2>&1; rc=$?; } ; " +
                        "echo '===== WINRM DISABLE END (exit='$rc') =====' >> %s; exit $rc\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteRolesDir,
                logFile,
                invPath, pbPath, logFile,
                logFile
        );
        return execAndWait(jobId, cmd, logFile);
    }

    // ===== DC post bootstrap (OU/Group/User trên DC sau khi promote) =====
    // Tạo inventory 1 host Windows (WinRM NAT)
    private String buildWinInventory(String ip, int port, String user, String pass) {
        return "[windows]\n" + ip + "\n\n[windows:vars]\n" +
                "ansible_user=" + user + "\n" +
                "ansible_password=" + pass + "\n" +
                "ansible_port=" + port + "\n" +
                "ansible_connection=winrm\n" +
                "ansible_winrm_scheme=http\n" +
                "ansible_winrm_transport=basic\n" +
                "ansible_winrm_server_cert_validation=ignore\n" +
                "ansible_winrm_read_timeout_sec=900\n" +
                "ansible_winrm_operation_timeout_sec=120\n";
    }

    // Render 1 playbook chạy đúng 1 role + vars
    private String buildRolePlaybook(String role, Map<String,Object> vars) {
        StringBuilder rolesYaml = new StringBuilder();
        rolesYaml.append("  - role: ").append(role).append("\n");
        if (vars != null) {
            for (Map.Entry<String,Object> e : vars.entrySet()) {
                rolesYaml.append("    ")
                        .append(e.getKey())
                        .append(": ")
                        .append(yamlScalar(e.getValue()))
                        .append("\n");
            }
        }
        return "- hosts: windows\n" +
                "  gather_facts: yes\n" +
                "  roles:\n" + rolesYaml;
    }

    /**
     * Chạy role theo mẫu “viết file → export ROLES_PATH → ansible-playbook”.
     * logTag dùng để đặt tên file log cho dễ tra.
     */
    private boolean runRoleOnce(String subJobId,
                                String ip, int port, String user, String pass,
                                String roleName, Map<String,Object> vars,
                                String logTag) {
        try {
            // 1) Upload inventory + playbook
            String invPath = remoteJobsDir + "/inventory_" + subJobId + ".ini";
            String pbPath  = remoteJobsDir + "/playbook_"  + subJobId + ".yml";
            writeRemoteFile(invPath, buildWinInventory(ip, port, user, pass));
            writeRemoteFile(pbPath,  buildRolePlaybook(roleName, vars));

            // 2) Đảm bảo thư mục logs tồn tại
            execLocal(String.format(
                    "ssh %s -p %d %s@%s \"mkdir -p %s\"",
                    SSH_OPTS, remotePort, remoteUser, remoteHost, remoteLogsDir
            ));

            // 3) Chạy ansible-playbook + export ROLES_PATH
            String logFile = remoteLogsDir + "/" + subJobId + "_" + logTag + ".log";
            String cmd = String.format(
                    "ssh %s -p %d %s@%s \"export ANSIBLE_ROLES_PATH='%s'; " +
                            "echo '===== %s START =====' >> %s; " +
                            "{ ansible-playbook -i %s %s >> %s 2>&1; rc=$?; } ; " +
                            "echo '===== %s END (exit='$rc') =====' >> %s; exit $rc\"",
                    SSH_OPTS, remotePort, remoteUser, remoteHost, remoteRolesDir,
                    logTag.toUpperCase(), logFile,
                    invPath, pbPath, logFile,
                    logTag.toUpperCase(), logFile
            );

            return execAndWait(subJobId, cmd, logFile);
        } catch (Exception ex) {
            log.error("runRoleOnce error ({})", roleName, ex);
            return false;
        }
    }

    public boolean runDcPostBootstrap(String subJobId, String ip, int port,
                                      String user, String pass,
                                      String domainName, String ouName,
                                      String adminGroupName, String adminUserName, String adminUserPass) {
        Map<String,Object> v = new HashMap<>();
        v.put("domain_name",       domainName);
        v.put("ou_name",           ouName);
        v.put("admin_group_name",  adminGroupName);
        v.put("admin_user_name",   adminUserName);
        v.put("admin_user_pass",   adminUserPass);
        // role = dc_post_bootstrap
        return runRoleOnce(subJobId, ip, port, user, pass,
                "dc_post_bootstrap", v, "dc_post_bootstrap");
    }

    public boolean runAdAccountBootstrap(String subJobId, String ip, int port,
                                         String user, String pass,
                                         String domainName, String ouName,
                                         String groupName, String newUser, String newPass) {
        Map<String,Object> v = new HashMap<>();
        v.put("domain_name",   domainName);
        v.put("ou_name",       ouName);
        v.put("group_name",    groupName);
        v.put("new_user_name", newUser);
        v.put("new_user_pass", newPass);
        // role = ad_account_bootstrap
        return runRoleOnce(subJobId, ip, port, user, pass,
                "ad_account_bootstrap", v, "ad_account_bootstrap");
    }

    public boolean runJoinDomain(String subJobId, String ip, int port,
                                 String localAdmin, String localPass,
                                 String domainName, String dcIp,
                                 String domainUser, String domainPass,
                                 String rdpGrantUser) {
        Map<String,Object> v = new HashMap<>();
        v.put("win_version",          "11");       // có thể tham số hoá nếu cần
        v.put("domain_name",          domainName);
        v.put("domain_controller_ip", dcIp);
        v.put("domain_user",          domainUser);
        v.put("domain_password",      domainPass);
        v.put("rdp_grant_user",       rdpGrantUser);
        // role = join_domain
        return runRoleOnce(subJobId, ip, port, localAdmin, localPass,
                "join_domain", v, "join_domain");
    }

    // === Helper: render giá trị an toàn cho YAML ===
    private String yamlScalar(Object v) {
        if (v == null) return "''";                // chuỗi rỗng
        if (v instanceof Number || v instanceof Boolean) {
            return String.valueOf(v);              // số/boolean: giữ nguyên
        }
        // List/Map: serialize JSON rồi quote lại (tránh phải tự vẽ YAML phức tạp)
        if (v instanceof java.util.Collection || v instanceof java.util.Map) {
            try {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v);
                return "'" + json.replace("'", "''") + "'";
            } catch (Exception ignore) {
                String s = String.valueOf(v).replace("'", "''");
                return "'" + s + "'";
            }
        }
        // Mặc định: string – quote + escape single quote
        String s = String.valueOf(v).replace("'", "''");
        return "'" + s + "'";
    }

    /** SSH chạy gọn, không log file, trả true/false theo exit code */
    private boolean execAndStream(String jobId, java.util.List<String> args, long timeoutMinutes) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            RUNNING.put(jobId, p);

            Thread t = new Thread(() -> {
                try (var br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        log.info("[{}] {}", jobId, line);
                    }
                } catch (Exception ignored) {}
            }, "ssh-stream-" + jobId);
            t.setDaemon(true);
            t.start();

            boolean finished = p.waitFor(timeoutMinutes, java.util.concurrent.TimeUnit.MINUTES);
            if (!finished) {
                try { p.destroyForcibly(); } catch (Exception ignored) {}
                RUNNING.remove(jobId);
                log.warn("[{}] timeout khi chạy SSH", jobId);
                return false;
            }

            int exit = p.exitValue();
            RUNNING.remove(jobId);
            log.info("[{}] SSH exit={}", jobId, exit);
            return exit == 0;
        } catch (Exception e) {
            log.error("execAndStream(args) error", e);
            return false;
        }
    }
    /**
     * Kiểm tra TCP connect từ Ansible host tới host:port.
     * Log theo thời gian thực từng lần thử (INFO), không ghi log ra file từ xa.
     */
    public boolean waitPortOpenFromAnsibleHost(String host, int port,
                                               int attempts, int delaySeconds, int timeoutSeconds) {
        log.info("NAT wait-port START: {}:{} (attempts={}, delay={}s, timeoutPerTry={}s)",
                host, port, attempts, delaySeconds, timeoutSeconds);

        // Script chạy TRÊN máy Ansible (Ubuntu). Không dùng seq để tránh phụ thuộc.
        String remoteScript = String.format(
                "PATH=/usr/sbin:/usr/bin:/sbin:/bin; " +
                        "i=1; " +
                        "while [ \"$i\" -le %d ]; do " +
                        "  echo \"[portcheck] try $i/%d -> %s:%d\"; " +
                        "  if command -v nc >/dev/null 2>&1; then " +
                        "    echo \"[portcheck] using nc -z -w %d\"; " +
                        "    if nc -z -w %d %s %d; then echo \"[portcheck] OK\"; exit 0; else echo \"[portcheck] not open\"; fi; " +
                        "  else " +
                        "    echo \"[portcheck] using /dev/tcp with timeout %ds\"; " +
                        "    if timeout %d bash -lc 'exec 3<>/dev/tcp/%s/%d'; then echo \"[portcheck] OK\"; exit 0; else echo \"[portcheck] not open\"; fi; " +
                        "  fi; " +
                        "  i=$((i+1)); sleep %d; " +
                        "done; " +
                        "echo \"[portcheck] GIVEUP after %d tries\"; " +
                        "exit 1",
                attempts, attempts, host, port,
                timeoutSeconds,
                timeoutSeconds, host, port,
                timeoutSeconds,
                timeoutSeconds, host, port,
                delaySeconds,
                attempts
        );

        // Gọi SSH bằng danh sách tham số (không đi qua cmd.exe/bash local)
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("ssh");
        cmd.add("-o"); cmd.add("StrictHostKeyChecking=no");
        cmd.add("-o"); cmd.add("UserKnownHostsFile=/dev/null");
        cmd.add("-p"); cmd.add(String.valueOf(remotePort));
        cmd.add(remoteUser + "@" + remoteHost);
        cmd.add("bash"); cmd.add("-lc"); cmd.add(remoteScript);

        String jobId = "portcheck_" + host + "_" + port;
        boolean ok = execAndStream(jobId, cmd, timeoutMinutes);
        log.info("NAT wait-port END: {}:{} -> {}", host, port, ok ? "READY" : "NOT READY");
        return ok;
    }


    /** Cài 1 app theo AppDefinition (role) */
    public boolean runPlanForApp(String subJobId, AppDefinition def,
                                 String ip, int port, String user, String pass,
                                 Map<String, Object> vars) {
        log.info("ippppppppppppppp: {}", ip);
        log.info("porttttttttttttt: {}", port);

        String logFile = remoteLogsDir + "/" + subJobId + ".log";

        // 1) Tạo inventory tạm (trỏ tới ipPublic + NAT WinRM port)
        String inv = "[windows]\n" + ip + "\n\n[windows:vars]\n" +
                "ansible_user=" + user + "\n" +
                "ansible_password=" + pass + "\n" +
                "ansible_port=" + port + "\n" +
                "ansible_connection=winrm\n" +
                "ansible_winrm_scheme=http\n" +
                "ansible_winrm_transport=basic\n" +
                "ansible_winrm_server_cert_validation=ignore\n"+
                "ansible_winrm_read_timeout_sec=900\n"+
                "ansible_winrm_operation_timeout_sec=120\n";
        String invPath = remoteJobsDir + "/inventory_" + subJobId + ".ini";
        try { writeRemoteFile(invPath, inv); } catch (Exception e) { return false; }

        // 2) Playbook gọi role theo AppDefinition
        StringBuilder rolesYaml = new StringBuilder();
        rolesYaml.append("  - role: ").append(def.getCode()).append("\n");
        if (vars != null) {
            for (Map.Entry<String,Object> e : vars.entrySet()) {
                rolesYaml.append("    ")
                        .append(e.getKey())
                        .append(": ")
                        .append(yamlScalar(e.getValue()))
                        .append("\n");
            }
        }

        String play =
                "- hosts: windows\n" +
                        "  gather_facts: yes\n" +
                        "  roles:\n" + rolesYaml;

        String pbPath = remoteJobsDir + "/playbook_" + subJobId + ".yml";
        try { writeRemoteFile(pbPath, play); } catch (Exception e) { return false; }

        // 3) SSH VÀO ANSIBLE HOST (đúng port) + export ROLES_PATH + chạy ansible-playbook
        String cmd = String.format(
                "ssh %s -p %d %s@%s \"mkdir -p %s; " +                       // đảm bảo thư mục logs có sẵn
                        "export ANSIBLE_ROLES_PATH='%s'; " +
                        "echo '===== APP %s START =====' >> %s; " +
                        "{ ansible-playbook -i %s %s >> %s 2>&1; rc=$?; } ; " +
                        "echo '===== APP %s END (exit='$rc') =====' >> %s; exit $rc\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteLogsDir,
                remoteRolesDir,
                def.getCode(), logFile,
                invPath, pbPath, logFile,
                def.getCode(), logFile
        );

        return execAndWait(subJobId, cmd, logFile);
    }


    /** Hàm dùng chung chạy SSH + chờ */
    private boolean execAndWait(String jobId, String command, String logFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(detectShell(), detectShellFlag(), command);
            Process p = pb.start();
            RUNNING.put(jobId, p);

            boolean finished = p.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                log.warn("SSH command timeout for job {}", jobId);
                try { p.destroyForcibly(); } catch (Exception ignored) {}
                RUNNING.remove(jobId);
                return false;
            }

            int exit = p.exitValue();
            RUNNING.remove(jobId);

            if (exit != 0) {
                log.error("SSH command exit={} for job {}. Check remote log: {}", exit, jobId, logFile);
            }
            return exit == 0;
        } catch (Exception e) {
            log.error("SSH execution error", e);
            return false;
        }
    }




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
                    "ssh %s -p %d %s@%s cat %s",
                    SSH_OPTS, remotePort, remoteUser, remoteHost, path
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
                        .append("ansible_password=Mbf@2468#13579\n") // TODO: move to secure config/secret
                        .append("ansible_port=").append(ansiblePort).append("\n")
                        .append("ansible_connection=winrm\n")
                        .append("ansible_winrm_transport=basic\n")
                        .append("ansible_winrm_server_cert_validation=ignore\n");

        // 2) Playbook – nhúng extraVars vào block role
        Map<String, Object> extraVars = Optional.ofNullable(req.getExtraVars()).orElse(new HashMap<>());

        String rolesYaml = req.getApps().stream()
                .map(app -> {
                    StringBuilder b = new StringBuilder();
                    b.append("  - role: ").append(app).append("\n");

                    // win_version: CHỈ thêm nếu có, và QUOTE
                    if (req.getWinVersion() != null && !String.valueOf(req.getWinVersion()).isBlank()) {
                        b.append("    win_version: ").append(yamlScalar(req.getWinVersion())).append("\n");
                    }

                    for (Map.Entry<String, Object> e : extraVars.entrySet()) {
                        b.append("    ")
                                .append(e.getKey())
                                .append(": ")
                                .append(yamlScalar(e.getValue()))
                                .append("\n");
                    }
                    return b.toString();
                })
                .collect(java.util.stream.Collectors.joining("\n"));


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
                "ssh %s -p %d %s@%s \"mkdir -p %s %s /ansible-host/pids\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteJobsDir, remoteLogsDir
        ));

        // 5) Chạy ansible-playbook, export ROLES_PATH & ghi ssh pid, APPEND log + marker END
        String command = String.format(
                "ssh %s -p %d %s@%s \"export ANSIBLE_ROLES_PATH='%s'; " +
                        "echo $$ > /ansible-host/pids/%s.sshpid; " +
                        "echo '===== ATTEMPT #%d START ansible-playbook =====' >> %s; " +
                        "{ ansible-playbook -i %s/%s %s/%s >> %s 2>&1; rc=$?; } ; " +
                        "echo '===== ATTEMPT #%d END (exit='$rc') =====' >> %s; " +
                        "exit $rc\"",
                SSH_OPTS, remotePort, remoteUser, remoteHost, remoteRolesDir,
                jobId,
                attemptNo, logFile,
                remoteJobsDir, inventoryFileName, remoteJobsDir, playbookFileName, logFile,
                attemptNo, logFile
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
                    "ssh %s -p %d %s@%s \"(test -f /ansible-host/pids/%s.pid && kill -9 \\$(cat /ansible-host/pids/%s.pid) 2>/dev/null || true); " +
                            "(test -f /ansible-host/pids/%s.sshpid && kill -9 \\$(cat /ansible-host/pids/%s.sshpid) 2>/dev/null || true); " +
                            "pkill -f playbook_%s.yml || true\"",
                    SSH_OPTS, remotePort, remoteUser, remoteHost,
                    jobId, jobId, jobId, jobId, jobId
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
        String scpCmd = String.format(
                "scp -P %d -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \"%s\" %s@%s:%s",
                remotePort, tmp, remoteUser, remoteHost, remotePath
        );
        execLocal(scpCmd);


        // xoá file tạm local
        Files.deleteIfExists(Paths.get(tmp.replace("/", FileSystems.getDefault().getSeparator())));
    }

    /** Append 1 dòng message vào log từ xa theo thời gian thực (giữ log cũ + thêm marker) */
    private void appendRemoteLog(String jobId, String message) {
        try {
            String logFile = remoteLogsDir + "/" + jobId + ".log";
            String cmd = String.format(
                    "ssh %s -p %d %s@%s \"echo '[%s] %s' >> %s\"",
                    SSH_OPTS, remotePort, remoteUser, remoteHost,
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
