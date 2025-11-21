package com.mobifone.vdi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.dto.response.InfraSuccessEvent;
import com.mobifone.vdi.entity.ProvisionTask;
import com.mobifone.vdi.entity.enumeration.TaskStatus;
import com.mobifone.vdi.repository.ProvisionTaskRepository;
import com.mobifone.vdi.utils.ProvisionSignalBus;
import com.mobifone.vdi.utils.ProvisionTimeoutTracker;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// com.mobifone.vdi.service.ProvisionPersistService
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProvisionPersistService {
    ProvisionTaskRepository repo;
    ObjectMapper om = new ObjectMapper();
    ProvisionTimeoutTracker tracker;
    ProvisionSignalBus signalBus;
    VirtualDesktopService virtualDesktopService;
    ProjectCascadeService projectCascadeService;
    AnsibleRunnerService ansible;

    public void createProvisioning(String taskId, int count) {
        ProvisionTask t = ProvisionTask.builder()
                .taskId(taskId)
                .status(TaskStatus.PROVISIONING)
                .build();
        repo.save(t);
        tracker.register(taskId, count);
    }

    // ✅ THÊM MỚI: khi bắt đầu xóa – tạo task ở trạng thái DELETING và lưu lại id instance
    public void createDeleting(String taskId, String idInstance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delete_instance_id", idInstance);

        ProvisionTask t = ProvisionTask.builder()
                .taskId(taskId)
                .status(TaskStatus.DELETING)
                .build();
        try { t.setInstanceFloatingPairs(om.writeValueAsString(payload)); } catch (Exception ignored) {}
        repo.save(t);

        // đặt deadline xóa (ví dụ: 10 phút)
        tracker.register(taskId, 1);
    }

    // Khi bắt đầu DESTROY infra (cả project)
    public void createDestroyingProject(String taskId, String projectId, String ownerUserId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destroy_project_id", projectId);
        payload.put("owner_user_id", ownerUserId);

        ProvisionTask t = ProvisionTask.builder()
                .taskId(taskId)
                .status(TaskStatus.DELETING) // hoặc DESTROYING nếu thêm enum
                .build();
        try { t.setInstanceFloatingPairs(om.writeValueAsString(payload)); } catch (Exception ignored) {}
        repo.save(t);
        tracker.register(taskId, 1);
    }

    // Xử lý result từ MQ cho cả delete instance & destroy infra
    @Transactional
    public void handleDeleteResult(String taskId, boolean ok, String rawMessage) {
        ProvisionTask task = repo.findByTaskId(taskId)
                .orElseGet(() -> ProvisionTask.builder().taskId(taskId).build());

        if (task.getStatus() == TaskStatus.DELETED || task.getStatus() == TaskStatus.DELETE_FAILED) {
            log.info("[DeleteResult] task {} already terminal: {}", taskId, task.getStatus());
            return;
        }

        task.setErrorMessage(rawMessage);

        final String payload   = task.getInstanceFloatingPairs();
        final String instanceId = extractStr(payload, "delete_instance_id");
        final String projectId  = extractStr(payload, "destroy_project_id");

        if (ok) {
            task.setStatus(TaskStatus.DELETED);
            try {
                // ================== CASE 1: DELETE 1 INSTANCE ==================
                if (instanceId != null && !instanceId.isBlank()) {
                    virtualDesktopService.findByIdInstanceOpt(instanceId).ifPresent(vd -> {
                        String wan = vd.getIpPublic();
                        String rdp = vd.getPortPublic();
                        String win = vd.getPortWinRmPublic();

                        // RDP NAT
                        if (wan != null && !wan.isBlank() && rdp != null && rdp.matches("\\d+")) {
                            try {
                                boolean delRdp = ansible.runNatDelete(taskId + "_rdp_del", wan, Integer.parseInt(rdp));
                                log.info("[DeleteResult] NAT delete RDP {}:{} => {}", wan, rdp, delRdp ? "OK" : "FAILED");
                            } catch (Exception ex) {
                                log.warn("[DeleteResult] NAT delete RDP error: {}", ex.getMessage());
                            }
                        }

                        // WinRM NAT
                        if (wan != null && !wan.isBlank() && win != null && win.matches("\\d+")) {
                            try {
                                boolean delWin = ansible.runNatDelete(taskId + "_winrm_del", wan, Integer.parseInt(win));
                                log.info("[DeleteResult] NAT delete WINRM {}:{} => {}", wan, win, delWin ? "OK" : "FAILED");
                            } catch (Exception ex) {
                                log.warn("[DeleteResult] NAT delete WINRM error: {}", ex.getMessage());
                            }
                        }
                    });

                    virtualDesktopService.deleteVirtualDesktopByIdInstance(instanceId);
                    log.info("[DeleteResult] DELETED instance {}, task {}", instanceId, taskId);

                    // ================== CASE 2: DESTROY PROJECT/INFRA ==================
                } else if (projectId != null && !projectId.isBlank()) {

                    // 2.1 XÓA NAT CHO TẤT CẢ VDI TRONG PROJECT
                    try {
                        var VDIs = virtualDesktopService.findAllByProject(projectId);
                        for (var vd : VDIs) {
                            String wan = vd.getIpPublic();
                            String rdp = vd.getPortPublic();
                            String win = vd.getPortWinRmPublic();

                            if (wan == null || wan.isBlank()) continue;

                            if (rdp != null && rdp.matches("\\d+")) {
                                try {
                                    boolean delRdp = ansible.runNatDelete(taskId + "_proj_rdp_" + vd.getId(), wan, Integer.parseInt(rdp));
                                    log.info("[DeleteResult] [Project {}] NAT delete RDP {}:{} => {}",
                                            projectId, wan, rdp, delRdp ? "OK" : "FAILED");
                                } catch (Exception ex) {
                                    log.warn("[DeleteResult] [Project {}] NAT delete RDP error for vd {}: {}",
                                            projectId, vd.getId(), ex.getMessage());
                                }
                            }

                            if (win != null && win.matches("\\d+")) {
                                try {
                                    boolean delWin = ansible.runNatDelete(taskId + "_proj_winrm_" + vd.getId(), wan, Integer.parseInt(win));
                                    log.info("[DeleteResult] [Project {}] NAT delete WINRM {}:{} => {}",
                                            projectId, wan, win, delWin ? "OK" : "FAILED");
                                } catch (Exception ex) {
                                    log.warn("[DeleteResult] [Project {}] NAT delete WINRM error for vd {}: {}",
                                            projectId, vd.getId(), ex.getMessage());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[DeleteResult] NAT cleanup for project {} failed: {}", projectId, ex.getMessage());
                    }

                    // 2.2 CASCADE XÓA PROJECT + VDI + USER_PROJECT + RESET PASS
                    projectCascadeService.cascadeMarkProjectDeleted(projectId);
                    log.info("[DeleteResult] DESTROYED project {}, task {}", projectId, taskId);

                } else {
                    log.warn("[DeleteResult] task {} ok=true nhưng thiếu payload", taskId);
                }
            } catch (Exception e) {
                log.warn("[DeleteResult] Cascade DB failed for task {}: {}", taskId, e.getMessage(), e);
            }
        } else {
            task.setStatus(TaskStatus.DELETE_FAILED);
            log.warn("[DeleteResult] task {} FAILED. raw={}", taskId, rawMessage);
        }

        repo.save(task);
        tracker.cancel(taskId);
        repo.findByTaskId(taskId).ifPresent(t -> signalBus.complete(taskId, t));
    }



    private String extractStr(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<?, ?> m = om.readValue(json, Map.class);
            Object v = m.get(key);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignored) { return null; }
    }


    /**
     * SUCCESS: parse theo 3 dạng response của OpenStack:
     * - Có pfsense_config  (organization)
     * - resources[].resource[] (add-resource)
     * - resources[] thuần instance (personal)
     */
    public void handleSuccess(InfraSuccessEvent event) {
        log.info("[InfraSuccess] parsed: {}", event);

        final String taskId = event.getIdentifier();
        ProvisionTask task = repo.findByTaskId(taskId)
                .orElseGet(() -> ProvisionTask.builder()
                        .taskId(taskId)
                        .status(TaskStatus.PROVISIONING)
                        .build());

        // ================== LẤY infraId ==================
        String infraId = event.getInfraId();  // case personal / org

        if (infraId == null && event.getResources() != null) {
            // case add-resource: resources[].infra_id
            infraId = event.getResources().stream()
                    .map(InfraSuccessEvent.ResourceGroup::getInfraId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        task.setInfraId(infraId);

        // ================== PARSE INSTANCES ==================
        List<Map<String, Object>> instances = new ArrayList<>();

        boolean hasPfSense = event.getPfsenseConfig() != null
                && event.getPfsenseConfig().getNetwork() != null
                && !event.getPfsenseConfig().getNetwork().isEmpty();

        String orgPublicIp = hasPfSense ? extractOrgProviderIp(event.getPfsenseConfig()) : null;

        if (event.getResources() != null) {
            for (InfraSuccessEvent.ResourceGroup group : event.getResources()) {

                // ---- 1) PERSONAL / ORG: resources[].instances[] ----
                if ("openstack_compute_instance_v2".equals(group.getType())) {
                    if (group.getInstances() != null) {
                        for (InfraSuccessEvent.Instance ins : group.getInstances()) {
                            InfraSuccessEvent.Attributes a = ins.getAttributes();
                            if (a == null) continue;

                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("instance_id", a.getId());
                            m.put("access_ip_v4", a.getAccessIpV4());
                            m.put("fixed_ip_v4", hasPfSense ? orgPublicIp : null);
                            if (hasPfSense) {
                                m.put("networks", event.getPfsenseConfig().getNetwork());
                            }
                            instances.add(m);
                        }
                    }
                }


                // ---- 2) ADD-RESOURCE: resources[].resourceItems[].instances[] ----
                if (group.getResourceItems() != null) {
                    for (InfraSuccessEvent.ResourceItem item : group.getResourceItems()) {

                        if (!"openstack_compute_instance_v2".equals(item.getType())) {
                            continue; // BỎ block-volume / network / phụ
                        }

                        for (InfraSuccessEvent.Instance ins : item.getInstances()) {
                            InfraSuccessEvent.Attributes a = ins.getAttributes();
                            if (a == null) continue;

                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("instance_id", a.getId());
                            m.put("access_ip_v4", a.getAccessIpV4());
                            m.put("fixed_ip_v4", null); // add-resource không có public IP riêng
                            instances.add(m);
                        }
                    }
                }
            }
        }

        try {
            task.setInstanceFloatingPairs(om.writeValueAsString(instances));
        } catch (Exception e) {
            task.setInstanceFloatingPairs("[]");
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setErrorMessage(null);
        repo.save(task);

        tracker.cancel(taskId);
        signalBus.complete(taskId, task);
    }

    /** Lấy public IP từ pfsense_config.network (mạng chứa EXTCLOUD_PROVIDER) */
    private String extractOrgProviderIp(InfraSuccessEvent.PfsenseConfig cfg) {
        if (cfg == null || cfg.getNetwork() == null) return null;

        for (InfraSuccessEvent.PfsenseConfig.Net n : cfg.getNetwork()) {
            String name = n.getName();
            if (name != null && name.contains("EXTCLOUD_PROVIDER")) {
                return n.getFixedIpV4();
            }
        }
        return null;
    }

    public void handleError(String taskId, String errorMessage) {
        ProvisionTask task = repo.findByTaskId(taskId)
                .orElseGet(() -> ProvisionTask.builder().taskId(taskId).build());
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        repo.save(task);
        tracker.cancel(taskId);

        signalBus.complete(taskId, task);
    }

    /** view: trả y hệt list “mỗi máy một item” nếu SUCCESS; nếu FAILED trả nguyên văn error */
    public Optional<Map<String, Object>> view(String taskId) {
        return repo.findByTaskId(taskId).map(t -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("task_id", t.getTaskId());
            out.put("status", t.getStatus().name());
            if (t.getStatus() == TaskStatus.FAILED) {
                out.put("error_message", t.getErrorMessage()); // nguyên văn
            } else if (t.getStatus() == TaskStatus.SUCCESS && t.getInstanceFloatingPairs() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    var list = (List<Map<String, Object>>) om.readValue(t.getInstanceFloatingPairs(), List.class);
                    out.put("instances", list);
                } catch (Exception ignore) {}
            }
            return out;
        });
    }

    // Thêm vào ProvisionPersistService
    public Optional<ProvisionTask> findEntity(String taskId) {
        return repo.findByTaskId(taskId);
    }


    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void sweepTimeouts() {
        var snapshot = tracker.snapshot();
        if (snapshot.isEmpty()) return;

        Instant now = Instant.now();
        snapshot.forEach((taskId, info) -> {
            if (now.isAfter(info.getDeadline())) {
                repo.findByTaskId(taskId).ifPresent(t -> {
                    if (t.getStatus() == TaskStatus.PROVISIONING) {
                        String msg = "Timeout after " + (info.getExpectedCount() * 6)
                                + " minutes (expected count: " + info.getExpectedCount() + ")";
                        t.setStatus(TaskStatus.FAILED);
                        t.setErrorMessage(msg);
                        repo.save(t);
                        log.warn("[Timeout] task {} => FAILED: {}", taskId, msg);
                    }
                    tracker.cancel(taskId);
                });
            }
        });
    }
}

