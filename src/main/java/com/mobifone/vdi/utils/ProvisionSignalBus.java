package com.mobifone.vdi.utils;

import com.mobifone.vdi.entity.ProvisionTask;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProvisionSignalBus {
    private final ConcurrentHashMap<String, CompletableFuture<ProvisionTask>> bus = new ConcurrentHashMap<>();

    /** Lấy (hoặc tạo) Future cho taskId */
    public CompletableFuture<ProvisionTask> future(String taskId) {
        return bus.computeIfAbsent(taskId, k -> new CompletableFuture<>());
    }

    /** Hoàn tất future với ProvisionTask (SUCCESS/FAILED) */
    public void complete(String taskId, ProvisionTask task) {
        CompletableFuture<ProvisionTask> f = bus.computeIfAbsent(taskId, k -> new CompletableFuture<>());
        if (!f.isDone()) {
            f.complete(task);
        }
    }

    /** Tuỳ chọn: fail future nếu có exception ở listener */
    public void completeExceptionally(String taskId, Throwable t) {
        CompletableFuture<ProvisionTask> f = bus.computeIfAbsent(taskId, k -> new CompletableFuture<>());
        if (!f.isDone()) {
            f.completeExceptionally(t);
        }
    }
}
