package com.mobifone.vdi.utils;

import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProvisionTimeoutTracker {

    @Value
    public static class DeadlineInfo {
        Instant deadline;
        int expectedCount;
    }

    private final Map<String, DeadlineInfo> deadlines = new ConcurrentHashMap<>();

    public void register(String taskId, int count) {
        Instant dl = Instant.now().plusSeconds(count * 120L); // 2 phút / instance
        deadlines.put(taskId, new DeadlineInfo(dl, count));
    }

    public void cancel(String taskId) {
        deadlines.remove(taskId);
    }

    public DeadlineInfo get(String taskId) {
        return deadlines.get(taskId);
    }

    public Map<String, DeadlineInfo> snapshot() {
        return Map.copyOf(deadlines);
    }
}
