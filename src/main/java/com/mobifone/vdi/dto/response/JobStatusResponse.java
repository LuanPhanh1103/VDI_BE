package com.mobifone.vdi.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobStatusResponse {
    String jobId;
    String status;
    String mode;
    int totalVMs;
    int successVMs;
    int failedVMs;
    String message;

    List<StepEntry> steps; // ✨ timeline cấp job
    List<JobVDISnapshot> virtualDesktops;

    @Data @Builder
    public static class StepEntry {
        String step;      // create_instance|save_db|assign_interface|create_nat|apps|winrm_disable|exception|install:<code>
        String status;    // INFO|SUCCESS|FAILED
        String detail;    // logPath hoặc error message ngắn
        LocalDateTime at; // thời điểm
    }

    @Data @Builder
    public static class JobVDISnapshot {
        String id;
        String name;
        String ipLocal;
        String ipPublic;
        Integer portLocal;
        Integer portPublic;
        String status;
        boolean winrmDisabled;
        String idInstance;       // ✨ nếu muốn show instance_id
        List<AppResult> apps;
        List<StepEntry> steps;   // ✨ timeline cấp VDI
    }

    @Data @Builder
    public static class AppResult {
        String appCode;
        String actionType;
        String status;
        String logPath;
    }
}
