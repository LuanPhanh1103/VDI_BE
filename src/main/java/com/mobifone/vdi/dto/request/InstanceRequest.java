package com.mobifone.vdi.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InstanceRequest {
    @NotNull
    @NotEmpty
    String vol_type;

    @NotNull
    @NotEmpty
    String base_vol_id;

    @NotNull
    Long vol_size;

    @NotNull
    @NotEmpty
    String flavor_id;

    @NotNull
    Long count;

    String projectId;

    String region;
}
