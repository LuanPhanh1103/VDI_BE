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
public class VNCRequest {
    @NotNull
    @NotEmpty
    String name;

    @NotNull
    @NotEmpty
    String ip;

    @NotNull
    @NotEmpty
    String port;
}
