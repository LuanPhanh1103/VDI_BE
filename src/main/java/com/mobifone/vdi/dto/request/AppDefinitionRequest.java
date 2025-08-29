// com.mobifone.vdi.dto.request.AppDefinitionRequest.java
package com.mobifone.vdi.dto.request;


import com.mobifone.vdi.entity.enumeration.ActionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppDefinitionRequest {
    String code;                         // "chrome"
    String name;                         // "Google Chrome"
    ActionType actionType;               // INSTALL | CONFIG
    List<String> winVersion;             // ["10","11","2019","2022"]
    List<String> linuxVersion;           // ["ubuntu-22.04", ...]
    List<String> extraVars;       // {"account_name":"Administrator"}
    Boolean enabled;
}
