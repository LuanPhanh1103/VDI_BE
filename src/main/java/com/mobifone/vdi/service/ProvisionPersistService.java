package com.mobifone.vdi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    // ✅ THÊM MỚI: xử lý kết quả delete từ MQ
    public void handleDeleteResult(String taskId, boolean ok, String rawMessage) {
        ProvisionTask task = repo.findByTaskId(taskId)
                .orElseGet(() -> ProvisionTask.builder().taskId(taskId).build());

        // Lưu raw trong errorMessage để truy vết (đúng như bạn yêu cầu “lấy tin nhắn raw thôi”)
        task.setErrorMessage(rawMessage);

        if (ok) {
            task.setStatus(TaskStatus.DELETED);
            String idInstance = extractDeleteInstanceId(task.getInstanceFloatingPairs());
            if (idInstance != null && !idInstance.isBlank()) {
                try {
                    // ✅ GỌI XOÁ VD KHI DELETE OK
                    virtualDesktopService.deleteVirtualDesktopByIdInstance(idInstance);
                } catch (Exception e) {
                    log.warn("deleteVirtualDesktopByIdInstance({}) failed: {}", idInstance, e.getMessage());
                }
            } else {
                log.warn("No delete_instance_id stored for task {}", taskId);
            }
        } else {
            task.setStatus(TaskStatus.DELETE_FAILED);
        }

        repo.save(task);
        tracker.cancel(taskId);
        if (signalBus != null) repo.findByTaskId(taskId).ifPresent(t -> signalBus.complete(taskId, t));
    }

    // ✅ THÊM MỚI: lấy ra id instance đã lưu khi createDeleting
    private String extractDeleteInstanceId(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = om.readValue(json, Map.class);
            Object v = m.get("delete_instance_id");
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignored) { return null; }
    }

    /** SUCCESS: gom theo VM, chỉ set fixed_ip_v4 nếu có network "provider"; nếu không → null */

    public void handleSuccess(InfraSuccessEvent event) {
        log.info("aaaaaaaaaaaaaaaaaaaaaaaaa even: {}", event);
        final String taskId = event.getIdentifier();

        ProvisionTask task = repo.findByTaskId(taskId)
                .orElseGet(() -> ProvisionTask.builder()
                        .taskId(taskId)
                        .status(TaskStatus.PROVISIONING)
                        .build());

        // ✅ lấy fixed_ip_v4 đầu tiên từ pfsense_config.network (nếu có)
        final String fixedFromPfSense = getFirstPfsenseFixedIp(event);

        List<Map<String, Object>> machines = new ArrayList<>();

        if (event.getCreatedResources() != null) {
            event.getCreatedResources().stream()
                    .filter(cr -> "openstack_compute_instance_v2".equals(cr.getType()))
                    .filter(cr -> cr.getInstances() != null)
                    .forEach(cr -> cr.getInstances().forEach(ins -> {
                        if (ins.getAttributes() == null) return;
                        var attr = ins.getAttributes();

                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("instance_id",   attr.getId());
                        item.put("access_ip_v4",  attr.getAccessIpV4());  // IP trong attributes của VM
                        item.put("fixed_ip_v4",   fixedFromPfSense);      // IP lấy từ pfsense_config.network[0].fixed_ip_v4 (nếu có)
                        machines.add(item);
                    }));
        }

        try {
            task.setInstanceFloatingPairs(om.writeValueAsString(machines));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        task.setStatus(TaskStatus.SUCCESS);
        task.setErrorMessage(null);
        repo.save(task);

        tracker.cancel(taskId);
        signalBus.complete(taskId, task);
    }

    /** Lấy fixed_ip_v4 đầu tiên trong pfsense_config.network; không kiểm tra tên mạng */
    private String getFirstPfsenseFixedIp(InfraSuccessEvent event) {
        if (event == null || event.getPfsenseConfig() == null) return null;
        var nets = event.getPfsenseConfig().getNetwork();
        if (nets == null || nets.isEmpty()) return null;

        for (var n : nets) {
            if (n != null && n.getFixedIpV4() != null && !n.getFixedIpV4().isBlank()) {
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

