package com.mobifone.vdi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.dto.request.AppPlanRequest;
import com.mobifone.vdi.dto.request.InstanceRequest;
import com.mobifone.vdi.dto.request.ProvisionAndConfigureRequest;
import com.mobifone.vdi.dto.response.JobStatusResponse;
import com.mobifone.vdi.entity.*;
import com.mobifone.vdi.entity.enumeration.TaskStatus;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.repository.*;
import com.mobifone.vdi.utils.ProvisionSignalBus;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

// ... imports giữ nguyên ...

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProvisionOrchestratorService {

    // ====== Injects giữ nguyên ======
    DeploymentJobRepository jobRepo;
    JobStepLogRepository stepRepo;
    AppDeploymentRepository appRepo;

    VirtualDesktopRepository vdRepo;
    UserService userService;
    ProjectRepository projectRepo;

    OpenStackService openStackService;
    AnsibleRunnerService ansible;
    PortAllocatorService portAllocator;
    AppDefinitionRepository appDefinitionRepository;
    ProvisionSignalBus signalBus;
    Executor taskExecutor;
    ProvisionTaskRepository provisionTaskRepository;

    ObjectMapper om = new ObjectMapper();

    @NonFinal
    @Value("${provision.infra-timeout-minutes:6}")
    long infraTimeoutMinutes;

    private static final int MAX_DETAIL_LEN = 2000;
    private static final int MAX_MESSAGE_LEN = 1000;

    @NonFinal
    @Value("${provision.infra-timeout-grace-seconds:90}") // <— thêm GRACE 90s
    long infraTimeoutGraceSeconds;

    private ProvisionTask waitInfraOrFallback(String infraTaskId) throws Exception {
        // future đã được tạo trong signalBus
        var fut = signalBus.future(infraTaskId);
        try {
            // chờ chính: phút
            return fut.get(infraTimeoutMinutes, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException te) {
            log.warn("Infra wait timed out at {}m → applying {}s grace window", infraTimeoutMinutes, infraTimeoutGraceSeconds);
            try {
                // chờ bù thêm (giây) – xử lý trễ MQ/serialization
                return fut.get(infraTimeoutGraceSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException ignore) {
                // fallback cuối: đọc DB (phòng TH event tới đúng lúc nhưng future chưa complete)
                return provisionTaskRepository.findByTaskId(infraTaskId).orElse(null);
            }
        }
    }

    private String stripAnsi(String s) {
        if (s == null) return null;
        // loại bỏ mã màu ANSI, \x1b[31m ...
        return s.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private String clamp(String s, int max) {
        if (s == null) return null;
        String clean = stripAnsi(s);
        if (clean.length() <= max) return clean;
        return clean.substring(0, max - 15) + "...(truncated)";
    }


    // ====== API nhận job, chạy nền ======
    public String submit(String mode, ProvisionAndConfigureRequest req, String osRegion) {
        String jobId = UUID.randomUUID().toString();

        DeploymentJob job = DeploymentJob.builder()
                .id(jobId).mode(mode)
                .requesterId(userService.getMyInfo().getId())
                .status("PENDING")
                .totalVms(Math.toIntExact(Math.max(1, req.getCount())))
                .successVms(0).failedVms(0)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        jobRepo.save(job);

        taskExecutor.execute(() -> {
            try { run(jobId, mode, req, osRegion); }
            catch (Exception e) {
                log.error("Orchestrator crashed", e);
                DeploymentJob j = jobRepo.findById(jobId).orElseThrow();
                j.setStatus("FAILED");
                j.setMessage("Exception: " + e.getMessage());
                j.setUpdatedAt(LocalDateTime.now());
                jobRepo.save(j);
            }
        });

        return jobId;
    }

    // ====== Orchestrate toàn bộ flow ======
    public void run(String jobId, String mode, ProvisionAndConfigureRequest req, String osRegion) {
        DeploymentJob job = jobRepo.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepo.save(job);

        int total = job.getTotalVms();
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            String vdName = (total == 1) ? req.getName() : req.getName() + "-" + (i + 1);
            VirtualDesktop vd = null;

            try {
                final String usernameOfVdi = Optional.ofNullable(req.getUsernameOfVdi())
                        .filter(s -> !s.isBlank())
                        .orElseThrow(() -> new AppException(ErrorCode.MISSING_USERNAME_OF_VDI));
                // 1) Gọi infra & chờ signal (KO poll DB)
                String infraTaskId = openStackService.provisionWithRetry(mode, toInstanceRequest(req), req.getUserId(), osRegion);
                logStep(jobId, null, "create_instance", "INFO", "taskId=" + infraTaskId);

                log.info("Waiting infra task {} up to {} minutes ...", infraTaskId, infraTimeoutMinutes);
                ProvisionTask infraTask = waitInfraOrFallback(infraTaskId);

                if (infraTask == null) {
                    // thật sự không có kết quả
                    throw stepError(jobId, null, "create_instance", "Infra timeout – no event received");
                }

                if (infraTask.getStatus() != TaskStatus.SUCCESS) {
                    String err = infraTask.getErrorMessage();
                    String shortErr = clamp(err, 1000);
                    logStep(jobId, null, "create_instance", "FAILED", shortErr);

                    job.setFailedVms(job.getFailedVms() + 1);
                    job.setStatus(job.getSuccessVms() == 0 ? "FAILED" : "PARTIAL_FAILED");
                    job.setMessage(clamp("Provision infra FAILED: " + shortErr, MAX_MESSAGE_LEN));
                    job.setUpdatedAt(LocalDateTime.now());
                    jobRepo.save(job);
                    return; // <-- DỪNG HẲN, KHÔNG CHẠY NAT/APP
                }

                // Parse instances: [{instance_id, access_ip_v4, fixed_ip_v4}]
                List<Map<String, Object>> instances = om.readValue(
                        infraTask.getInstanceFloatingPairs(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        }
                );
                if (instances == null || instances.isEmpty()) {
                    throw stepError(jobId, null, "create_instance", "Infra returned empty instances");
                }
                Map<String, Object> inst = instances.getFirst();
                String instanceId = String.valueOf(inst.get("instance_id"));
                String ipLocal    = String.valueOf(inst.get("access_ip_v4"));
                String ipPublic   = inst.get("fixed_ip_v4") == null ? null : String.valueOf(inst.get("fixed_ip_v4"));

                // 2) Lưu VDI (kèm instance_id, portPublic unique)
                int portPublic = portAllocator.allocateUnique();
                vd = buildVD(jobId, vdName, req, ipLocal, ipPublic, portPublic, instanceId);
                // ✅ CẤP THÊM PORT NAT CHO WINRM (5985) & LƯU VÀO VDI
                int winRmPortPublic = portAllocator.allocateUnique();
                vd.setPortWinRmPublic(String.valueOf(winRmPortPublic));
                vd = vdRepo.save(vd);
                logStep(jobId, vd.getId(), "save_db", "SUCCESS", "vdId=" + vd.getId());

                // 3) Delay 10s cho pfSense ổn định
                safeSleep(10_000);

                // 3.1) ORGANIZATION: enable-assign-interface
                if ("organization".equalsIgnoreCase(mode)) {
                    String assignName  = req.getUserId();
                    String assignType  = "static";
                    String assignDescr = req.getUserId();
                    String assignIp    = toGatewayIp(ipLocal); // a.b.c.1
                    int assignMask     = 24;

                    logStep(jobId, vd.getId(), "assign_interface",
                            "INFO", "assign_name=" + assignName + ", assign_ip=" + assignIp + "/24");

                    boolean assignOk = ansible.runAssignInterface(jobId, assignName, assignType, assignDescr, assignIp, assignMask);
                    if (!assignOk) {
                        vd = markVDIFailed(vd, "ASSIGN_IF_ERROR");
                        logStep(jobId, vd.getId(), "assign_interface", "FAILED",
                                "/ansible-host/logs/" + jobId + "_assign_interface.log");
                        failed.incrementAndGet();
                        continue; // qua VM tiếp theo
                    }
                    logStep(jobId, vd.getId(), "assign_interface", "SUCCESS",
                            "/ansible-host/logs/" + jobId + "_assign_interface.log");
                }

                // 3.2) NAT RDP 3389 → portPublic
                if (ipPublic == null || ipPublic.isBlank()) {
                    vd = markVDIFailed(vd, "NAT_ERROR");
                    logStep(jobId, vd.getId(), "create_nat", "FAILED", "No public IP (fixed_ip_v4) from infra");
                    failed.incrementAndGet();
                    continue;
                }

                boolean natOk = ansible.runNatCreate(jobId, vd.getIpPublic(), portPublic, ipLocal, 3389);
                if (!natOk) {
                    vd = markVDIFailed(vd, "NAT_ERROR");
                    logStep(jobId, vd.getId(), "create_nat", "FAILED", "/ansible-host/logs/" + jobId + "_nat.log");
                    failed.incrementAndGet();
                    continue;
                }
                logStep(jobId, vd.getId(), "create_nat", "SUCCESS", "/ansible-host/logs/" + jobId + "_nat.log");

                // ✅ 3.3) NAT WINRM 5985 → winrmPortPublic
                boolean natWinrmOk = ansible.runNatCreate(
                        jobId + "_winrm",            // sub-job để log riêng
                        ipPublic,                    // WAN IP
                        winRmPortPublic,             // cổng public NAT
                        ipLocal,                     // target (local IP)
                        5985                         // local port
                );
                if (!natWinrmOk) {
                    vd = markVDIFailed(vd, "NAT_ERROR");
                    logStep(jobId, vd.getId(), "create_nat_winrm", "FAILED",
                            "/ansible-host/logs/" + jobId + "_winrm_nat.log");
                    failed.incrementAndGet();
                    continue;
                }
                logStep(jobId, vd.getId(), "create_nat_winrm", "SUCCESS",
                        "/ansible-host/logs/" + jobId + "_winrm_nat.log");

                // Cho pfSense apply rule 1 chút
                safeSleep(10_000);

                // 4) Cài/Config app (song song) – seed để UI thấy trước
                seedAppDeployment(jobId, vd.getId(), req.getApps());

                VirtualDesktop finalVd = vd;
                List<Boolean> results = Optional.ofNullable(req.getApps()).orElse(List.of())
                        .parallelStream()
                        .map(plan -> runApp(jobId, finalVd, plan, usernameOfVdi))
                        .toList();

                boolean allOk = results.isEmpty() || results.stream().allMatch(Boolean::booleanValue);
                if (!allOk) {
                    vd = markVDIFailed(vd, "APP_ERROR");
                    logStep(jobId, vd.getId(), "apps", "FAILED", "One or more apps failed");
                    failed.incrementAndGet();
                    continue;
                }
                logStep(jobId, vd.getId(), "apps", "SUCCESS", null);

                // 5) Disable WinRM
                String winVersionForDisable = pickWinVersion(req);
                boolean winrmDisabled = ansible.runWinRmDisable(
                        jobId,
                        vd.getIpPublic(),                     // ✅ chạy qua NAT WinRM (ipPublic)
                        vd.getPortWinRmPublic(),              // ✅ cổng NAT WinRM
                        usernameOfVdi,
                        vd.getPassword(),
                        winVersionForDisable
                );
                vd.setWinRmDisabled(winrmDisabled);
                vd.setStatus(winrmDisabled ? "READY" : "READY_WITH_WARN");
                vdRepo.save(vd);

                logStep(jobId, vd.getId(), "winrm_disable",
                        winrmDisabled ? "SUCCESS" : "FAILED",
                        "/ansible-host/logs/" + jobId + "_winrm_disable.log");

                // ✅ 6) XÓA NAT WinRM (dù disable OK/KO cũng nên dọn)
                boolean delOk = ansible.runNatDelete(
                        jobId + "_winrm_del",                 // sub-job để log riêng
                        vd.getIpPublic(),
                        Integer.parseInt(vd.getPortWinRmPublic())
                );
                logStep(jobId, vd.getId(), "delete_nat_winrm",
                        delOk ? "SUCCESS" : "FAILED",
                        "/ansible-host/logs/" + jobId + "_winrm_del_nat_delete.log");


                success.incrementAndGet();

            } catch (Exception e) {
                logStep(jobId, (vd != null ? vd.getId() : null),
                        "exception", "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());

                if (vd != null && "CREATED".equals(vd.getStatus())) {
                    markVDIFailed(vd, "ERROR");
                }
                failed.incrementAndGet();

                // ✅ THỬ DỌN NAT WINRM NẾU ĐÃ CẤP
                try {
                    if (vd != null && vd.getIpPublic() != null && vd.getPortWinRmPublic() != null) {
                        ansible.runNatDelete(jobId + "_winrm_del",
                                vd.getIpPublic(),
                                Integer.parseInt(vd.getPortWinRmPublic()));
                        logStep(jobId, vd.getId(), "delete_nat_winrm_on_error", "INFO",
                                "/ansible-host/logs/" + jobId + "_winrm_del_nat_delete.log");
                    }
                } catch (Exception ignore) {}
            }

        }

        job.setSuccessVms(success.get());
        job.setFailedVms(failed.get());
        job.setStatus(failed.get()==0 ? "SUCCESS" : (success.get()==0 ? "FAILED" : "PARTIAL_FAILED"));
        job.setMessage(buildSummary(job.getId()));
        job.setUpdatedAt(LocalDateTime.now());
        jobRepo.save(job);
    }

    // ===== Helpers =====

    /** Gắn lỗi vào JobStep exception cho throw */
    private RuntimeException stepError(String jobId, String vdId, String step, String msg) {
        logStep(jobId, vdId, step, "FAILED", msg);
        log.error("stepError: {}", msg);
        return new AppException(ErrorCode.API_PROVISION_ERR);
    }

    /** Đặt trạng thái lỗi cho VDI và lưu DB */
    private VirtualDesktop markVDIFailed(VirtualDesktop vd, String status) {
        vd.setStatus(status);
        return vdRepo.save(vd);
    }

    private void safeSleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /** Run một app theo AppDefinition + validate requiredVars */
    private boolean runApp(String jobId, VirtualDesktop vd, AppPlanRequest plan, String usernameOfVdi) {
        AppDefinition def = appDefinitionRepository.findByCode(plan.getCode())
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_NOT_FOUND));

        ensureRequiredVars(def, plan);

        Map<String, Object> merged = new LinkedHashMap<>();
        if (plan.getVars() != null) merged.putAll(plan.getVars());
        if (plan.getWinVersion() != null)   merged.put("win_version", plan.getWinVersion());
        if (plan.getLinuxVersion() != null) merged.put("linux_version", plan.getLinuxVersion());
        if (plan.getActionType() != null)   merged.put("action_type", plan.getActionType());

        String subJobId = jobId + "_" + def.getCode();

        AppDeployment ad = appRepo.findByJobId(jobId).stream()
                .filter(x -> x.getVdId().equals(vd.getId()) && x.getAppCode().equals(def.getCode()))
                .findFirst()
                .orElse(AppDeployment.builder()
                        .id(UUID.randomUUID().toString())
                        .vdId(vd.getId())
                        .jobId(jobId)
                        .appCode(def.getCode())
                        .actionType(plan.getActionType() != null ? plan.getActionType() : def.getActionType().name())
                        .status("PENDING")
                        .retryCount(0)
                        .startedAt(LocalDateTime.now())
                        .build());

        ad.setStatus("RUNNING");
        ad.setStartedAt(LocalDateTime.now());
        appRepo.save(ad);

        logStep(jobId, vd.getId(), "install:" + def.getCode(), "INFO", "start");

        boolean ok = ansible.runPlanForApp(
                subJobId, def, vd.getIpPublic(), Integer.parseInt(vd.getPortWinRmPublic()),
                usernameOfVdi, vd.getPassword(), merged
        );

        ad.setStatus(ok ? "SUCCESS" : "FAILED");
        ad.setFinishedAt(LocalDateTime.now());
        ad.setLogPath("/ansible-host/logs/" + subJobId + ".log");
        appRepo.save(ad);

        logStep(jobId, vd.getId(), "install:" + def.getCode(), ok ? "SUCCESS" : "FAILED", ad.getLogPath());
        return ok;
    }

    private void seedAppDeployment(String jobId, String vdId, List<AppPlanRequest> apps) {
        if (apps == null || apps.isEmpty()) return;
        for (AppPlanRequest p : apps) {
            appRepo.save(AppDeployment.builder()
                    .id(UUID.randomUUID().toString())
                    .vdId(vdId)
                    .jobId(jobId)
                    .appCode(p.getCode())
                    .actionType(Optional.ofNullable(p.getActionType()).orElse("CONFIG"))
                    .status("PENDING")
                    .retryCount(0)
                    .startedAt(LocalDateTime.now())
                    .build());
        }
    }

    private void ensureRequiredVars(AppDefinition def, AppPlanRequest plan) {
        List<String> reqs = Optional.ofNullable(def.getRequiredVars()).orElse(List.of());
        if (reqs.isEmpty()) return;

        Map<String, Object> vars = Optional.ofNullable(plan.getVars()).orElse(Map.of());
        List<String> missing = new ArrayList<>();

        for (String k : reqs) {
            if (!vars.containsKey(k)) { missing.add(k); continue; }
            Object v = vars.get(k);
            if (v == null) { missing.add(k); continue; }
            if (v instanceof String s && s.trim().isEmpty()) { missing.add(k); }
        }

        if (!missing.isEmpty()) {
            log.error("Thiếu biến bắt buộc cho app {}: {}", def.getCode(), String.join(",", missing));
            throw new AppException(ErrorCode.MISSING_REQUIRED_VARS);
        }
    }

    private String toGatewayIp(String ipLocal) {
        if (ipLocal == null) return null;
        String[] p = ipLocal.trim().split("\\.");
        if (p.length != 4) return ipLocal;
        return p[0] + "." + p[1] + "." + p[2] + ".1";
    }

    private InstanceRequest toInstanceRequest(ProvisionAndConfigureRequest req) {
        InstanceRequest ir = new InstanceRequest();
        ir.setVol_type(req.getVol_type());
        ir.setBase_vol_id(req.getBase_vol_id());
        ir.setVol_size(req.getVol_size());
        ir.setFlavor_id(req.getFlavor_id()); // <— chú ý tên getter của bạn
        ir.setCount(req.getCount());
        return ir;
    }

    /** LƯU Ý: thêm đối số instanceId và gán vào vd.setIdInstance(...) */
    private VirtualDesktop buildVD(String jobId, String name, ProvisionAndConfigureRequest req,
                                   String ipLocal, String ipPublic, int portPublic, String instanceId) {
        VirtualDesktop vd = new VirtualDesktop();
        vd.setName(name);
        vd.setIpLocal(ipLocal);
        vd.setIpPublic(ipPublic);
        vd.setPassword("Mbf@2468#13579");
        vd.setPortLocal("3389");
        vd.setPortPublic(String.valueOf(portPublic));

        vd.setCpu(req.getCpu());
        vd.setGpu(req.getGpu());
        vd.setRam(req.getRam());

        vd.setVolumeType(req.getVolumeType());
        vd.setVolumeSize(req.getVolumeSize());
        vd.setHasGPU(req.getHasGPU());
        vd.setTypeVirtualDesktop(req.getTypeVirtualDesktop());

        vd.setStatus("CREATED");
        vd.setWinRmDisabled(false);
        vd.setJobId(jobId);
        vd.setIdInstance(instanceId); // <— GÁN Ở ĐÂY

        vd.setUser(userService.findUserById(req.getUserId()));
        vd.setProject(projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED)));

        try {
            Map<String,Object> plan = new LinkedHashMap<>();

            if (req.getApps() != null && !req.getApps().isEmpty()) {
                List<Map<String,Object>> apps = new ArrayList<>();
                for (AppPlanRequest p : req.getApps()) {
                    Map<String,Object> a = new LinkedHashMap<>();
                    a.put("code", p.getCode());
                    if (p.getActionType() != null) a.put("actionType", p.getActionType());
                    if (p.getWinVersion() != null) a.put("winVersion", p.getWinVersion());
                    if (p.getLinuxVersion() != null) a.put("linuxVersion", p.getLinuxVersion());
                    if (p.getVars() != null && !p.getVars().isEmpty()) a.put("vars", p.getVars());
                    apps.add(a);
                }
                plan.put("apps", apps);
            }
            // Chỉ thêm nếu có giá trị
            if (req.getActionType() != null) plan.put("actionType", req.getActionType());
            if (req.getWinVersion() != null) plan.put("winVersion", req.getWinVersion());
            if (req.getLinuxVersion() != null) plan.put("linuxVersion", req.getLinuxVersion());
            if (req.getExtraVars() != null && !req.getExtraVars().isEmpty()) plan.put("extraVars", req.getExtraVars());

            vd.setAnsiblePlan(om.writeValueAsString(plan));
        } catch (Exception ignore) {}


        return vd;
    }

    private String pickWinVersion(ProvisionAndConfigureRequest req) {
        if (req.getApps() != null) {
            for (AppPlanRequest p : req.getApps()) {
                if (p.getWinVersion() != null && !p.getWinVersion().isBlank()) {
                    return p.getWinVersion();
                }
            }
        }
        if (req.getWinVersion() != null && !req.getWinVersion().isBlank()) {
            return req.getWinVersion();
        }
        return "2022";
    }

    private void logStep(String jobId, String vdId, String step, String status, String detail) {
        JobStepLog j = JobStepLog.builder()
                .id(UUID.randomUUID().toString())
                .jobId(jobId)
                .vdId(vdId)
                .step(step)
                .status(status)
                .detail(clamp(detail, MAX_DETAIL_LEN))
                .createdAt(LocalDateTime.now())
                .build();
        try {
            stepRepo.save(j);
        } catch (Exception e) {
            // fallback: nếu vẫn lỗi do schema, giảm thêm
            j.setDetail(clamp(j.getDetail(), 500));
            try { stepRepo.save(j); } catch (Exception ignored) {}
        }
    }


    /** Tổng hợp summary theo VDI: nếu có FAILED, báo bước cuối cùng bị FAILED; nếu READY báo OK */
    private String buildSummary(String jobId) {
        List<JobStepLog> steps = stepRepo.findByJobIdOrderByCreatedAtAsc(jobId);
        Map<String, List<JobStepLog>> byVd = new LinkedHashMap<>();
        for (JobStepLog s : steps) {
            String key = s.getVdId() == null ? "__job__" : s.getVdId();
            byVd.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        StringBuilder sb = new StringBuilder();
        byVd.forEach((vdId, list) -> {
            Optional<JobStepLog> lastFail = list.stream()
                    .filter(x -> "FAILED".equalsIgnoreCase(x.getStatus()))
                    .reduce((a, b) -> b);
            if ("__job__".equals(vdId)) {
                lastFail.ifPresentOrElse(
                        lf -> sb.append("[JOB] lỗi ở bước ").append(lf.getStep())
                                .append(" → ").append(Optional.ofNullable(lf.getDetail()).orElse("")).append("\n"),
                        () -> sb.append("[JOB] OK\n")
                );
            } else {
                String name = vdRepo.findById(vdId).map(VirtualDesktop::getName).orElse(vdId);
                lastFail.ifPresentOrElse(
                        lf -> sb.append("[").append(name).append("] lỗi ở bước ").append(lf.getStep())
                                .append(" → ").append(Optional.ofNullable(lf.getDetail()).orElse("")).append("\n"),
                        () -> sb.append("[").append(name).append("] OK\n")
                );
            }
        });
        return sb.toString().trim();
    }

    // ====== API view kèm timeline các step ======
    public JobStatusResponse getStatus(String jobId) {
        DeploymentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_JOB_NOT_FOUND));
        List<VirtualDesktop> vds = vdRepo.findByJobId(jobId);
        List<JobStepLog> steps = stepRepo.findByJobIdOrderByCreatedAtAsc(jobId);

        Map<String, List<JobStepLog>> stepsByVd = new HashMap<>();
        for (JobStepLog s : steps) {
            stepsByVd.computeIfAbsent(Objects.toString(s.getVdId(), "__job__"), k -> new ArrayList<>()).add(s);
        }

        List<JobStatusResponse.JobVDISnapshot> vdSnaps = vds.stream().map(vd -> {
            List<AppDeployment> apps = appRepo.findByVdIdOrderByStartedAtAsc(vd.getId());
            List<JobStatusResponse.AppResult> ars = apps.stream().map(a ->
                    JobStatusResponse.AppResult.builder()
                            .appCode(a.getAppCode())
                            .actionType(a.getActionType())
                            .status(a.getStatus())
                            .logPath(a.getLogPath())
                            .build()
            ).toList();

            // ✨ TIMELINE STEP CHO VDI
            List<JobStatusResponse.StepEntry> timeline = stepsByVd
                    .getOrDefault(vd.getId(), List.of())
                    .stream()
                    .map(s -> JobStatusResponse.StepEntry.builder()
                            .step(s.getStep())
                            .status(s.getStatus())
                            .detail(s.getDetail())
                            .at(s.getCreatedAt())
                            .build())
                    .toList();

            return JobStatusResponse.JobVDISnapshot.builder()
                    .id(vd.getId())
                    .name(vd.getName())
                    .ipLocal(vd.getIpLocal())
                    .ipPublic(vd.getIpPublic())
                    .portLocal(Integer.parseInt(vd.getPortLocal()))
                    .portPublic(Integer.parseInt(vd.getPortPublic()))
                    .status(vd.getStatus())
                    .winrmDisabled(Boolean.TRUE.equals(vd.getWinRmDisabled()))
                    .idInstance(vd.getIdInstance())  // <— nếu bạn thêm field này trong DTO
                    .apps(ars)
                    .steps(timeline)                  // <— ✨
                    .build();
        }).toList();

        // ✨ TIMELINE STEP CẤP JOB (vdId = null)
        List<JobStatusResponse.StepEntry> jobTimeline = stepsByVd
                .getOrDefault("__job__", List.of())
                .stream()
                .map(s -> JobStatusResponse.StepEntry.builder()
                        .step(s.getStep())
                        .status(s.getStatus())
                        .detail(s.getDetail())
                        .at(s.getCreatedAt())
                        .build())
                .toList();

        return JobStatusResponse.builder()
                .jobId(jobId)
                .status(job.getStatus())
                .mode(job.getMode())
                .totalVMs(job.getTotalVms())
                .successVMs(job.getSuccessVms())
                .failedVMs(job.getFailedVms())
                .message(job.getMessage())
                .steps(jobTimeline)                 // <— ✨
                .virtualDesktops(vdSnaps)
                .build();
    }
}
