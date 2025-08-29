package com.mobifone.vdi.dto.response;

import com.mobifone.vdi.entity.enumeration.HasGPU;
import com.mobifone.vdi.entity.enumeration.VolumeType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VirtualDesktopResponse {
    String id;              // cứng
    String name;            // cứng
    String ipLocal;
    String ipPublic;
    String password;        // cứng
    HasGPU hasGPU;          // cứng
    String portLocal;
    String portPublic;      // cứng
    String CPU;             // cứng
    String GPU;             // cứng
    String RAM;             // cứng
    VolumeType volumeType;
    String volumeSize;
    String idInstance;
//    String userId;
//    String projectId;
    String typeVirtualDesktop;
    UserBrief user;
    ProjectBrief project;
}
