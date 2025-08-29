package com.mobifone.vdi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.dto.response.InfraSuccessEvent;
import com.mobifone.vdi.entity.ProvisionTask;
import com.mobifone.vdi.entity.enumeration.TaskStatus;
import com.mobifone.vdi.repository.ProvisionTaskRepository;
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

    public void createProvisioning(String taskId, int count) {
        ProvisionTask t = ProvisionTask.builder()
                .taskId(taskId)
                .status(TaskStatus.PROVISIONING)
                .build();
        repo.save(t);
        tracker.register(taskId, count);
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

        // 1) Lấy fixed_ip_v4 từ pfsense_config.name == "provider"
        final String providerFixedIp =
                (event.getPfsenseConfig() != null
                        && event.getPfsenseConfig().getNetwork() != null)
                        ? event.getPfsenseConfig().getNetwork().stream()
                        .filter(n -> "provider".equalsIgnoreCase(n.getName()))
                        .map(InfraSuccessEvent.PfsenseConfig.Net::getFixedIpV4)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null)
                        : null;

        if (providerFixedIp == null) {
            log.warn("[handleSuccess] Không tìm thấy fixed_ip_v4 của provider trong pfsense_config; taskId={}", taskId);
        }

        // 2) Gom từng VM: instance_id + access_ip_v4 + fixed_ip_v4(provider)
        List<Map<String, Object>> machines = new ArrayList<>();

        if (event.getCreatedResources() != null) {
            event.getCreatedResources().stream()
                    .filter(cr -> "openstack_compute_instance_v2".equals(cr.getType()))
                    .filter(cr -> cr.getInstances() != null)
                    .forEach(cr -> cr.getInstances().forEach(ins -> {
                        if (ins.getAttributes() == null) return;

                        var attr = ins.getAttributes();
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("instance_id", attr.getId());
                        item.put("access_ip_v4", attr.getAccessIpV4()); // lấy trực tiếp từ attributes
                        item.put("fixed_ip_v4", providerFixedIp);       // IP provider từ pfsense_config
                        machines.add(item);
                    }));
        }

        // 3) Lưu DB & kết thúc
        try {
            task.setInstanceFloatingPairs(om.writeValueAsString(machines)); // tái dụng field để lưu JSON list
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        task.setStatus(TaskStatus.SUCCESS);
        task.setErrorMessage(null);
        repo.save(task);

        tracker.cancel(taskId);
    }

    public void handleError(String taskId, String errorMessage) {
        ProvisionTask task = repo.findByTaskId(taskId)
                .orElseGet(() -> ProvisionTask.builder().taskId(taskId).build());
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(errorMessage);
        repo.save(task);
        tracker.cancel(taskId);
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

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void sweepTimeouts() {
        var snapshot = tracker.snapshot();
        if (snapshot.isEmpty()) return;

        Instant now = Instant.now();
        snapshot.forEach((taskId, info) -> {
            if (now.isAfter(info.getDeadline())) {
                repo.findByTaskId(taskId).ifPresent(t -> {
                    if (t.getStatus() == TaskStatus.PROVISIONING) {
                        String msg = "Timeout after " + (info.getExpectedCount() * 2)
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

