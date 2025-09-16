package com.mobifone.vdi.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppPlanRequest {
    @NotBlank String code;          // trỏ vào AppDefinition.code
    String actionType;              // INSTALL|CONFIG (optional)
    String winVersion;              // optional
    String linuxVersion;            // optional
    Map<String, Object> vars;       // extra vars riêng cho app
}
