package com.mobifone.vdi.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InstanceResponse {
    private String task_id;      // FE dùng để poll
    private String status;       // PROVISIONING
    private String message;      // "Infra is being provisioned"
    private String raw;          // optional: resp gốc từ infra
}
