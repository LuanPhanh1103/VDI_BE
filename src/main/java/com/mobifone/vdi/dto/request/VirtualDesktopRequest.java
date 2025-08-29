package com.mobifone.vdi.dto.request;

import com.mobifone.vdi.entity.enumeration.HasGPU;
import com.mobifone.vdi.entity.enumeration.VolumeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VirtualDesktopRequest {
    @NotNull
    @NotEmpty
    String name;

    @NotNull
    @NotEmpty
    String ipLocal;

    @NotNull
    @NotEmpty
    String ipPublic;

    @NotNull
    @NotEmpty
    @NotBlank
    String password;

    @NotNull
    @NotEmpty
    String portLocal;

    @NotNull
    @NotEmpty
    String portPublic;

    @NotNull
    @NotEmpty
    String CPU;

    @NotNull
    @NotEmpty
    String GPU;

    @NotNull
    @NotEmpty
    String RAM;

    @NotNull
    VolumeType volumeType;

    @NotNull
    @NotEmpty
    String volumeSize;

    @NotNull
    @NotEmpty
    String idInstance;

    @NotNull
    HasGPU hasGPU;

    String typeVirtualDesktop;

    @NotNull(message = "User ID must not be null")
    String userId;

    @NotNull(message = "Project ID must not be null")
    String projectId;
}
