// com.mobifone.vdi.dto.request.AppDefinitionRequest.java
package com.mobifone.vdi.dto.request;


import com.mobifone.vdi.entity.enumeration.ActionType;
import com.mobifone.vdi.entity.enumeration.RunType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppDefinitionRequest {
    String code;
    String name;
    ActionType actionType;
    List<String> winVersion;
    List<String> linuxVersion;

    // ✅ giữ lại Map
    List<String> requiredVars;

    Boolean enabled;

    // ✅ giữ 2 trường này
    RunType runType;     // ROLE|PLAYBOOK
    String roleName;     // nếu ROLE
    String playbookPath; // nếu PLAYBOOK
}
