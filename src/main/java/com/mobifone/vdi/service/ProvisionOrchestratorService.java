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
import com.mobifone.vdi.repository.DeploymentJobRepository;
import com.mobifone.vdi.repository.JobStepLogRepository;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProvisionOrchestratorService {

    // ====== Chỉ inject SERVICE, không inject repository ======
    DeploymentJobRepository jobRepo;          // (jobRepo thuộc domain job – Orchestrator sở hữu)
    JobStepLogRepository stepRepo;            // (logRepo thuộc domain job – Orchestrator sở hữu)

    AppDeploymentService appDeploymentService;
    AppDefinitionService appDefinitionService;
    VirtualDesktopService virtualDesktopService;
    UserService userService;
    ProjectService projectService;            // chỉ dùng load entity (không tạo vòng vì ProjectService KHÔNG phụ thuộc Orchestrator)
    OpenStackService openStackService;
    AnsibleRunnerService ansible;
    PortAllocatorService portAllocator;
    ProvisionSignalBus signalBus;
    Executor taskExecutor;
    ProvisionPersistService provisionPersistService;

    ObjectMapper om = new ObjectMapper();

    @NonFinal
    @Value("${provision.infra-timeout-minutes:6}")
    long infraTimeoutMinutes;

    private static final int MAX_DETAIL_LEN = 2000;
    private static final int MAX_MESSAGE_LEN = 1000;

    @NonFinal
    @Value("${provision.infra-timeout-grace-seconds:90}")
    long infraTimeoutGraceSeconds;

    private ProvisionTask waitInfraOrFallback(String infraTaskId) throws Exception {
        var fut = signalBus.future(infraTaskId);
        try {
            return fut.get(infraTimeoutMinutes, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException te) {
            log.warn("Infra wait timed out at {}m → applying {}s grace window",
                    infraTimeoutMinutes, infraTimeoutGraceSeconds);
            try {
                return fut.get(infraTimeoutGraceSeconds, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException ignore) {
                // Fallback: đọc DB qua service persist (KHÔNG dùng repo trực tiếp)
                return provisionPersistService.findEntity(infraTaskId).orElse(null);
            }
        }
    }

    private String stripAnsi(String s) {
        if (s == null) return null;
        return s.replaceAll("\\u001B\\[[;\\d]*m", "");
    }

    private String clamp(String s, int max) {
        if (s == null) return null;
        String clean = stripAnsi(s);
        if (clean.length() <= max) return clean;
        return clean.substring(0, max - 15) + "...(truncated)";
    }

    /** Nhận job, chạy nền */
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

    /** Orchestrate toàn bộ flow */
    public void run(String jobId, String mode, ProvisionAndConfigureRequest req, String osRegion) {
        DeploymentJob job = jobRepo.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepo.save(job);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed  = new AtomicInteger(0);

        // --- Validate đầu vào dùng chung ---
        final String usernameOfVdi = Optional.ofNullable(req.getUsernameOfVdi())
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new AppException(ErrorCode.MISSING_USERNAME_OF_VDI));

        // ===========================================================
        // 1) GỌI INFRA 1 LẦN & CHỜ KẾT QUẢ
        // ===========================================================
        String infraTaskId = openStackService.provisionWithRetry(
                mode, toInstanceRequest(req), req.getUserId(), osRegion);
        logStep(jobId, null, "create_instance", "INFO", "taskId=" + infraTaskId);

        log.info("Waiting infra task {} up to {} minutes ...", infraTaskId, infraTimeoutMinutes);
        ProvisionTask infraTask;
        try {
            infraTask = waitInfraOrFallback(infraTaskId);
        } catch (Exception e) {
            throw stepError(jobId, "Infra wait error: " + e.getMessage());
        }
        if (infraTask == null) {
            throw stepError(jobId, "Infra timeout – no event received");
        }
        if (infraTask.getStatus() != TaskStatus.SUCCESS) {
            String err = clamp(infraTask.getErrorMessage(), 1000);
            logStep(jobId, null, "create_instance", "FAILED", err);
            job.setFailedVms(0);
            job.setSuccessVms(0);
            job.setStatus("FAILED");
            job.setMessage(clamp("Provision infra FAILED: " + err, MAX_MESSAGE_LEN));
            job.setUpdatedAt(LocalDateTime.now());
            jobRepo.save(job);
            return;
        }

        final String infraId = infraTask.getInfraId();
        if (infraId == null || infraId.isBlank()) {
            throw stepError(jobId, "infraId missing from infra task");
        }
        logStep(jobId, null, "infra_id", "INFO", "infraId=" + infraId);

        // Parse instances: [{instance_id, access_ip_v4, fixed_ip_v4}]
        List<Map<String, Object>> instances;
        try {
            instances = om.readValue(infraTask.getInstanceFloatingPairs(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception ex) {
            throw stepError(jobId, "Bad infra payload: " + ex.getMessage());
        }
        if (instances == null || instances.isEmpty()) {
            throw stepError(jobId, "Infra returned empty instances");
        }

        // Cập nhật lại tổng số VM theo thực tế infra trả về
        job.setTotalVms(instances.size());
        jobRepo.save(job);

        // ===========================================================
        // THIẾT LẬP BIẾN DÙNG CHUNG & CHẠY SONG SONG (STAGGER 10s)
        // ===========================================================
        final String baseName = Optional.ofNullable(req.getName()).filter(s -> !s.isBlank()).orElse("vdi");

        final boolean planHasDC  = planHasDomainController(req);
        final Optional<String> domainFromPlan = extractDomainNameFromPlan(req);

        try (ExecutorService vexec = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<?>[] futures = IntStream.range(0, instances.size())
                    .mapToObj(idx -> {
                        long delaySec = idx * 10L; // mỗi instance bắt đầu trễ hơn instance trước 10s
                        Executor delayedVexec = CompletableFuture.delayedExecutor(delaySec, TimeUnit.SECONDS, vexec);
                        return CompletableFuture.runAsync(() -> processOneInstance(
                                idx, instances, baseName,
                                mode, planHasDC, domainFromPlan,
                                jobId, req, usernameOfVdi, osRegion,
                                success, failed, infraId
                        ), delayedVexec);
                    })
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join(); // chờ tất cả xong
        } catch (Exception ex) {
            log.error("Parallel execution failed", ex);
        }

        // Hoàn tất job
        job.setSuccessVms(success.get());
        job.setFailedVms(failed.get());
        job.setStatus(failed.get()==0 ? "SUCCESS" : (success.get()==0 ? "FAILED" : "PARTIAL_FAILED"));
        job.setMessage(buildSummary(job.getId()));
        job.setUpdatedAt(LocalDateTime.now());
        jobRepo.save(job);
    }

    private String extractOrgProviderIp(Map<String, Object> inst) {
        Object netsRaw = inst.get("networks");
        if (!(netsRaw instanceof List<?> nets)) return null;

        for (Object o : nets) {
            if (o instanceof Map<?, ?> n) {
                String name = Objects.toString(n.get("name"), "");
                if (name.contains("EXTCLOUD_PROVIDER")) {
                    return Objects.toString(n.get("fixed_ip_v4"), null);
                }
            }
        }
        return null;
    }

    private void processOneInstance(
            int i,
            List<Map<String, Object>> instances,
            String baseName,
            String mode,
            boolean planHasDC,
            Optional<String> domainFromPlan,
            String jobId,
            ProvisionAndConfigureRequest req,
            String usernameOfVdi,
            String osRegion,
            AtomicInteger success,
            AtomicInteger failed,
            String infraId
    ) {
        final Map<String, Object> inst = instances.get(i);
        final String vdName = (instances.size() == 1) ? baseName : baseName + "-" + (i + 1);

        final String instanceId = String.valueOf(inst.get("instance_id"));
        final String ipLocal    = String.valueOf(inst.get("access_ip_v4"));
        String ipPublic   = inst.get("fixed_ip_v4") == null ? null : String.valueOf(inst.get("fixed_ip_v4"));

        if ("personal".equalsIgnoreCase(mode)) {
            ipPublic = "42.1.65.60";
        } else if ("add-resource-for-personal".equalsIgnoreCase(mode)) {
            // Lấy infraId từ task → tìm VDI personal đầu tiên trong DB
            VirtualDesktop vdp = virtualDesktopService.findAnyPersonalVDI(req.getUserId(), osRegion)
                    .orElseThrow(() -> new AppException(ErrorCode.ORCHESTRATOR_SERVICE_IP_PUBLIC_PERSONAL));
            ipPublic = vdp.getIpPublic(); // dùng cùng 42.x.x.x
        } else if ("organization".equalsIgnoreCase(mode)) {
            ipPublic = extractOrgProviderIp(inst); // hàm viết bên dưới
        } else if ("add-resource".equalsIgnoreCase(mode)) {
            VirtualDesktop vdo = virtualDesktopService.findAnyByProject(req.getProjectId(), osRegion)
                    .orElseThrow(() -> new AppException(ErrorCode.ORCHESTRATOR_SERVICE_IP_PUBLIC_ORG));
            ipPublic = vdo.getIpPublic();
        }

        final boolean isOrgMode  = "organization".equalsIgnoreCase(mode);
        final boolean isAddMode  = "add-resource".equalsIgnoreCase(mode);

        VirtualDesktop vd = null;

        try {
            // =======================================================
            // 2) LƯU VDI (CẤP PORT RDP + WINRM RIÊNG)
            // =======================================================
            int rdpPortPublic   = portAllocator.allocateUnique();
            int winRmPortPublic = portAllocator.allocateUnique();

            vd = buildVD(jobId, vdName, req, ipLocal, ipPublic, rdpPortPublic, instanceId, osRegion, infraId);
            vd.setPortWinRmPublic(String.valueOf(winRmPortPublic));

            if (isOrgMode && planHasDC) {
                vd.setIsDomainController(true);
                domainFromPlan.ifPresent(vd::setDomainName);
            }

            vd = virtualDesktopService.save(vd);
            logStep(jobId, vd.getId(), "save_db", "SUCCESS", "vdId=" + vd.getId());

            // =======================================================
            // STEP 3) PFSENSE & NAT
            // =======================================================
            safeSleep(); // đợi pfSense ổn định

            if (isOrgMode) {
                String assignName  = Optional.ofNullable(req.getUserId()).map(id -> id.replaceAll("-", "")).orElse("PhanhCute");
                String assignType  = "static";
                String assignIp    = toGatewayIp(ipLocal);
                int assignMask     = 24;

                logStep(jobId, vd.getId(), "assign_interface",
                        "INFO", "assign_name=" + assignName + ", assign_ip=" + assignIp + "/24");

                boolean assignOk = ansible.runAssignInterface(jobId, assignName, assignType, assignName, assignIp, assignMask);
                if (!assignOk) {
                    vd = markVDIFailed(vd, "ASSIGN_IF_ERROR");
                    logStep(jobId, vd.getId(), "assign_interface", "FAILED",
                            "/ansible-host/logs/" + jobId + "_assign_interface.log");
                    failed.incrementAndGet();
                    return;
                }
                logStep(jobId, vd.getId(), "assign_interface", "SUCCESS",
                        "/ansible-host/logs/" + jobId + "_assign_interface.log");
            }

            if (ipPublic == null || ipPublic.isBlank()) {
                vd = markVDIFailed(vd, "NAT_ERROR");
                logStep(jobId, vd.getId(), "create_nat", "FAILED", "No public IP (fixed_ip_v4) from infra");
                failed.incrementAndGet();
                return;
            }

            boolean natOk = ansible.runNatCreate(jobId, ipPublic, rdpPortPublic, ipLocal, 3389);
            if (!natOk) {
                vd = markVDIFailed(vd, "NAT_ERROR");
                logStep(jobId, vd.getId(), "create_nat", "FAILED", "/ansible-host/logs/" + jobId + "_nat.log");
                failed.incrementAndGet();
                return;
            }
            logStep(jobId, vd.getId(), "create_nat", "SUCCESS", "/ansible-host/logs/" + jobId + "_nat.log");

            boolean natWinrmOk = ansible.runNatCreate(jobId + "_winrm_" + (i+1), ipPublic, winRmPortPublic, ipLocal, 5985);
            if (!natWinrmOk) {
                vd = markVDIFailed(vd, "NAT_ERROR");
                logStep(jobId, vd.getId(), "create_nat_winrm", "FAILED",
                        "/ansible-host/logs/" + jobId + "_winrm_nat.log");
                failed.incrementAndGet();
                return;
            }
            logStep(jobId, vd.getId(), "create_nat_winrm", "SUCCESS",
                    "/ansible-host/logs/" + jobId + "_winrm_nat.log");

            boolean natReady = ansible.waitPortOpenFromAnsibleHost(ipPublic, winRmPortPublic, 10, 20, 5);
            if (!natReady) {
                vd = markVDIFailed(vd, "NAT_ERROR");
                logStep(jobId, vd.getId(), "wait_nat_winrm", "FAILED",
                        ipPublic + ":" + winRmPortPublic + " not reachable");
                failed.incrementAndGet();
                return;
            }
            logStep(jobId, vd.getId(), "wait_nat_winrm", "SUCCESS",
                    ipPublic + ":" + winRmPortPublic + " reachable");

            // =======================================================
            // STEP 4) CÀI/CONFIG APP (song song trong 1 instance nếu nhiều app)
            // =======================================================
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
                return;
            }
            logStep(jobId, vd.getId(), "apps", "SUCCESS", null);

            // =======================================================
            // NHÁNH THEO MODE
            // =======================================================
            if (isOrgMode) {
                // Chỉ phần đặc thù khi có domain controller
                if (planHasDC) {
                    final String domain = domainFromPlan
                            .filter(s -> !s.isBlank())
                            .orElseThrow(() -> new AppException(ErrorCode.MISSING_REQUIRED_VARS));

                    final String ouName         = "MbfOU";
                    final String adminGroupName = "MbfAdminsss";
                    final String adminUserName2 = "MbfAdmin";
                    final String adminUserPass2 = randomStrongPass();

                    boolean okBoot = ansible.runDcPostBootstrap(
                            jobId + "_dc_boot_" + (i+1),
                            vd.getIpPublic(), Integer.parseInt(vd.getPortWinRmPublic()),
                            usernameOfVdi, vd.getPassword(),
                            domain, ouName, adminGroupName, adminUserName2, adminUserPass2
                    );
                    if (!okBoot) {
                        vd = markVDIFailed(vd, "APP_ERROR");
                        logStep(jobId, vd.getId(), "dc_post_bootstrap", "FAILED",
                                "/ansible-host/logs/" + jobId + "_dc_boot_" + (i+1) + ".log");
                        failed.incrementAndGet();
                        return;
                    }
                    logStep(jobId, vd.getId(), "dc_post_bootstrap", "SUCCESS",
                            "/ansible-host/logs/" + jobId + "_dc_boot_" + (i+1) + ".log");

                    // Gắn thông tin DC
                    vd.setIsDomainController(true);
                    vd.setDomainName(domain);
                    vd.setDomainOu(ouName);
                    vd.setDomainAccountUsername(adminUserName2);
                    vd.setDomainAccountPassword(adminUserPass2);
                }

                // ✅ PHẦN COMMON CHO CẢ CÓ/KO DC
                vd.setStatus("READY");
                virtualDesktopService.save(vd);
                success.incrementAndGet();
                return;
            }

            if (isAddMode) {
                VirtualDesktop dc = virtualDesktopService
                        .findDomainController(req.getProjectId(), vd.getRegion())
                        .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_NOT_FOUND));

                final String domain = Optional.ofNullable(dc.getDomainName())
                        .filter(s -> !s.isBlank())
                        .orElseThrow(() -> new AppException(ErrorCode.MISSING_REQUIRED_VARS));

                final String ouName  = Optional.ofNullable(dc.getDomainOu()).filter(s -> !s.isBlank()).orElse("MbfOU");
                final String group   = "VDIUsers";
                final String newUser = (instances.size() == 1) ? req.getDomainAccountUsername() : req.getDomainAccountUsername() + "-" + (i + 1);
                final String newPass = randomStrongPass();

                final String dcWanIp = dc.getIpPublic();
                final int    dcWinrm = Integer.parseInt(dc.getPortWinRmPublic());
                final String daUser  = "Administrator";
                final String daPass  = dc.getPassword();

                boolean okAcc = ansible.runAdAccountBootstrap(
                        jobId + "_adacct_" + (i+1),
                        dcWanIp, dcWinrm,
                        daUser, daPass,
                        domain, ouName, group, newUser, newPass
                );
                if (!okAcc) {
                    vd = markVDIFailed(vd, "APP_ERROR");
                    logStep(jobId, vd.getId(), "ad_account_bootstrap", "FAILED",
                            "/ansible-host/logs/" + jobId + "_adacct_" + (i+1) + ".log");
                    failed.incrementAndGet();
                    return;
                }
                logStep(jobId, vd.getId(), "ad_account_bootstrap", "SUCCESS",
                        "/ansible-host/logs/" + jobId + "_adacct_" + (i+1) + ".log");

                vd.setDomainName(domain);
                vd.setDomainOu(ouName);
                vd.setDomainAccountUsername(newUser);
                vd.setDomainAccountPassword(newPass);
                virtualDesktopService.save(vd);

                boolean okJoin = ansible.runJoinDomain(
                        jobId + "_join_" + (i+1),
                        vd.getIpPublic(), Integer.parseInt(vd.getPortWinRmPublic()),
                        usernameOfVdi, vd.getPassword(),
                        domain, dc.getIpLocal(),
                        newUser, newPass,
                        newUser
                );
                if (!okJoin) {
                    vd = markVDIFailed(vd, "APP_ERROR");
                    logStep(jobId, vd.getId(), "join_domain", "FAILED",
                            "/ansible-host/logs/" + jobId + "_join_" + (i+1) + ".log");
                    failed.incrementAndGet();
                    return;
                }
                logStep(jobId, vd.getId(), "join_domain", "SUCCESS",
                        "/ansible-host/logs/" + jobId + "_join_" + (i+1) + ".log");

//                boolean winrmDisabled = ansible.runWinRmDisable(
//                        jobId,
//                        ipPublic,
//                        vd.getPortWinRmPublic(),
//                        usernameOfVdi,
//                        vd.getPassword(),
//                        pickWinVersion(req)
//                );
//                vd.setWinRmDisabled(winrmDisabled);
//                vd.setStatus(winrmDisabled ? "READY" : "READY_WITH_WARN");
//                virtualDesktopService.save(vd);
//
//                logStep(jobId, vd.getId(), "winrm_disable",
//                        winrmDisabled ? "SUCCESS" : "FAILED",
//                        "/ansible-host/logs/" + jobId + "_winrm_disable.log");

                boolean delOk = ansible.runNatDelete(
                        jobId + "_winrm_del_" + (i+1),
                        ipPublic,
                        Integer.parseInt(vd.getPortWinRmPublic())
                );
                logStep(jobId, vd.getId(), "delete_nat_winrm",
                        delOk ? "SUCCESS" : "FAILED",
                        "/ansible-host/logs/" + jobId + "_winrm_del_nat_delete.log");

                success.incrementAndGet();
                return;
            }



            // Mặc định
            vd.setStatus("READY");
            virtualDesktopService.save(vd);
            success.incrementAndGet();

        } catch (Exception e) {
            logStep(jobId, (vd != null ? vd.getId() : null),
                    "exception", "FAILED", e.getClass().getSimpleName() + ": " + e.getMessage());

            if (vd != null && "CREATED".equals(vd.getStatus())) {
                markVDIFailed(vd, "ERROR");
            }
            failed.incrementAndGet();

            try {
                if (vd != null && vd.getIpPublic() != null && vd.getPortWinRmPublic() != null) {
                    ansible.runNatDelete(jobId + "_winrm_del_err_" + (i+1),
                            vd.getIpPublic(),
                            Integer.parseInt(vd.getPortWinRmPublic()));
                    logStep(jobId, vd.getId(), "delete_nat_winrm_on_error", "INFO",
                            "/ansible-host/logs/" + jobId + "_winrm_del_nat_delete.log");
                }
            } catch (Exception ignore) {}
        }
    }


    // ====== Helpers mới (đặt trong class) ======
    private boolean planHasDomainController(ProvisionAndConfigureRequest req) {
        return Optional.ofNullable(req.getApps()).orElse(List.of())
                .stream().anyMatch(a -> "domain_controller".equalsIgnoreCase(a.getCode()));
    }
    private Optional<String> extractDomainNameFromPlan(ProvisionAndConfigureRequest req) {
        if (req.getApps() == null) return Optional.empty();
        return req.getApps().stream()
                .filter(a -> "domain_controller".equalsIgnoreCase(a.getCode()))
                .map(a -> {
                    Map<String,Object> vars = a.getVars();
                    if (vars == null) return null;
                    Object v = vars.get("domain_name");
                    return (v == null) ? null : String.valueOf(v).trim();
                })
                .filter(s -> s != null && !s.isBlank())
                .findFirst();
    }
    private String randomStrongPass() {
        return "Mbf@" + UUID.randomUUID().toString().replace("-", "").substring(0,10) + "#";
    }



    // ===== Helpers =====

    private RuntimeException stepError(String jobId, String msg) {
        logStep(jobId, null, "create_instance", "FAILED", msg);
        log.error("stepError: {}", msg);
        return new AppException(ErrorCode.API_PROVISION_ERR);
    }

    private VirtualDesktop markVDIFailed(VirtualDesktop vd, String status) {
        vd.setStatus(status);
        return virtualDesktopService.save(vd);
    }

    private void safeSleep() {
        try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
    }

    // ====== Run một app theo AppDefinition + validate requiredVars ======
    private boolean runApp(String jobId, VirtualDesktop vd, AppPlanRequest plan, String usernameOfVdi) {
        // 1) Lấy AppDefinition & validate biến bắt buộc
        AppDefinition def = appDefinitionService.getEntityByCodeOrThrow(plan.getCode());
        ensureRequiredVars(def, plan);

        // 2) Gộp biến cho ansible
        Map<String, Object> merged = new LinkedHashMap<>();
        if (plan.getVars() != null) merged.putAll(plan.getVars());
        if (plan.getWinVersion() != null)   merged.put("win_version", plan.getWinVersion());
        if (plan.getLinuxVersion() != null) merged.put("linux_version", plan.getLinuxVersion());
        if (plan.getActionType() != null)   merged.put("action_type", plan.getActionType());

        String subJobId = jobId + "_" + def.getCode();

        // 3) Lấy/tạo AppDeployment
        AppDeployment ad = stepSeedIfAbsent(jobId, vd, def, plan);

        // 4) Đánh dấu RUNNING
        ad.setStatus("RUNNING");
        ad.setStartedAt(LocalDateTime.now());
        appDeploymentService.save(ad); // <-- thay appRepo

        logStep(jobId, vd.getId(), "install:" + def.getCode(), "INFO", "start");

        // 5) Chạy ansible
        boolean ok = ansible.runPlanForApp(
                subJobId, def, vd.getIpPublic(), Integer.parseInt(vd.getPortWinRmPublic()),
                usernameOfVdi, vd.getPassword(), merged
        );

        // 6) Lưu kết quả
        ad.setStatus(ok ? "SUCCESS" : "FAILED");
        ad.setFinishedAt(LocalDateTime.now());
        ad.setLogPath("/ansible-host/logs/" + subJobId + ".log");
        appDeploymentService.save(ad); // <-- thay appRepo

        logStep(jobId, vd.getId(), "install:" + def.getCode(), ok ? "SUCCESS" : "FAILED", ad.getLogPath());
        return ok;
    }

    /** Tìm theo (jobId, vdId, appCode); nếu chưa có thì tạo bản ghi PENDING */
    private AppDeployment stepSeedIfAbsent(String jobId, VirtualDesktop vd, AppDefinition def, AppPlanRequest plan) {
        return appDeploymentService
                .findOneByJobVdCode(jobId, vd.getId(), def.getCode())
                .orElseGet(() -> appDeploymentService.save(AppDeployment.builder()
                        .id(UUID.randomUUID().toString())
                        .vdId(vd.getId())
                        .jobId(jobId)
                        .appCode(def.getCode())
                        .actionType(plan.getActionType() != null ? plan.getActionType() : def.getActionType().name())
                        .status("PENDING")
                        .retryCount(0)
                        .startedAt(null)
                        .build()));
    }

    /** Seed danh sách app PENDING để UI nhìn thấy trước */
    private void seedAppDeployment(String jobId, String vdId, List<AppPlanRequest> apps) {
        if (apps == null || apps.isEmpty()) return;

        // để tránh trùng
        List<AppDeployment> existing = appDeploymentService.findByJobId(jobId);

        for (AppPlanRequest p : apps) {
            final String code = p.getCode();
            boolean dup = existing.stream()
                    .anyMatch(a -> Objects.equals(a.getVdId(), vdId) && Objects.equals(a.getAppCode(), code));
            if (dup) continue;

            AppDefinition def = appDefinitionService.getEntityByCodeOrThrow(code);

            AppDeployment ad = AppDeployment.builder()
                    .id(UUID.randomUUID().toString())
                    .vdId(vdId)
                    .jobId(jobId)
                    .appCode(code)
                    .actionType(p.getActionType() != null ? p.getActionType() : def.getActionType().name())
                    .status("PENDING")
                    .retryCount(0)
                    .startedAt(LocalDateTime.now())
                    .build();

            appDeploymentService.save(ad);
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
        ir.setFlavor_id(req.getFlavor_id());
        ir.setCount(req.getCount());
        ir.setProjectId(req.getProjectId());
        return ir;
    }

    private VirtualDesktop buildVD(String jobId, String name, ProvisionAndConfigureRequest req,
                                   String ipLocal, String ipPublic, int portPublic,
                                   String instanceId, String region, String infraId)
    {
        VirtualDesktop vd = new VirtualDesktop();
        vd.setName(name);
        vd.setIpLocal(ipLocal);
        vd.setIpPublic(ipPublic);
        vd.setPassword("Mbf@2468#13579");
        vd.setPortLocal("3389");
        vd.setPortPublic(String.valueOf(portPublic));
        vd.setRegion(region);

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
        vd.setIdInstance(instanceId);
        vd.setInfraId(infraId);

        vd.setUser(userService.findUserById(req.getUserId()));
        vd.setProject(projectService.loadEntity(req.getProjectId())); // <<< lấy entity qua service

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
            if (req.getActionType() != null) plan.put("actionType", req.getActionType());
            if (req.getWinVersion() != null) plan.put("winVersion", req.getWinVersion());
            if (req.getLinuxVersion() != null) plan.put("linuxVersion", req.getLinuxVersion());
            if (req.getExtraVars() != null && !req.getExtraVars().isEmpty()) plan.put("extraVars", req.getExtraVars());

            vd.setAnsiblePlan(om.writeValueAsString(plan));
        } catch (Exception ignore) {}

        return vd;
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
            j.setDetail(clamp(j.getDetail(), 500));
            try { stepRepo.save(j); } catch (Exception ignored) {}
        }
    }

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
                String name = virtualDesktopService.findById(vdId).map(VirtualDesktop::getName).orElse(vdId);
                lastFail.ifPresentOrElse(
                        lf -> sb.append("[").append(name).append("] lỗi ở bước ").append(lf.getStep())
                                .append(" → ").append(Optional.ofNullable(lf.getDetail()).orElse("")).append("\n"),
                        () -> sb.append("[").append(name).append("] OK\n")
                );
            }
        });
        return sb.toString().trim();
    }

    public JobStatusResponse getStatus(String jobId) {
        DeploymentJob job = jobRepo.findById(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_JOB_NOT_FOUND));
        List<VirtualDesktop> vds = virtualDesktopService.findByJobId(jobId); // bạn có thể bọc thêm method này trong service
        List<JobStepLog> steps = stepRepo.findByJobIdOrderByCreatedAtAsc(jobId);

        Map<String, List<JobStepLog>> stepsByVd = new HashMap<>();
        for (JobStepLog s : steps) {
            stepsByVd.computeIfAbsent(Objects.toString(s.getVdId(), "__job__"), k -> new ArrayList<>()).add(s);
        }

        List<JobStatusResponse.JobVDISnapshot> vdSnaps = vds.stream().map(vd -> {
            List<AppDeployment> apps = appDeploymentService.findByVdIdOrderByStartedAtAsc(vd.getId());
            List<JobStatusResponse.AppResult> ars = apps.stream().map(a ->
                    JobStatusResponse.AppResult.builder()
                            .appCode(a.getAppCode())
                            .actionType(a.getActionType())
                            .status(a.getStatus())
                            .logPath(a.getLogPath())
                            .build()
            ).toList();

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
                    .idInstance(vd.getIdInstance())
                    .apps(ars)
                    .steps(timeline)
                    .build();
        }).toList();

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
                .steps(jobTimeline)
                .virtualDesktops(vdSnaps)
                .build();
    }
}
