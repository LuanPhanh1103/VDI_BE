package com.mobifone.vdi.dto.request;

import com.mobifone.vdi.entity.enumeration.HasGPU;
import com.mobifone.vdi.entity.enumeration.VolumeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProvisionAndConfigureRequest {
    // ===== Infra =====
    @NotBlank String vol_type;
    @NotBlank String base_vol_id;
    @NotNull  Long   vol_size;
    @NotBlank String flavor_id;
    @NotNull  Long   count;

    @NotBlank String usernameOfVdi;

    // ===== Metadata VD =====
    @NotBlank String name;
    @NotBlank String cpu;

    String gpu;

    @NotBlank String ram;
    @NotNull
    VolumeType volumeType;
    @NotBlank String volumeSize;
    @NotNull
    HasGPU hasGPU;
    String typeVirtualDesktop;
    @NotBlank String userId;
    @NotBlank String projectId;

    // ===== Kế hoạch apps (per app) =====
    @NotNull List<AppPlanRequest> apps;

    // ===== Back-compat (optional) =====
    String actionType;
    String winVersion;
    String linuxVersion;
    Map<String, Object> extraVars;
}
