package com.mobifone.vdi.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlavorsResponse {
    String flavorId;
    String name;
    String ram;
    String disk;
    String cpu;
}
