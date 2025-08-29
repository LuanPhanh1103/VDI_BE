package com.mobifone.vdi.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnsibleJobMessageRequest {
    String jobId;
    List<String> targetIps; // danh sách IP
    List<String> apps;      // danh sách app/role cần cài
    String winVersion;      // nếu Windows thì truyền
    String linuxVersion;    // nếu Linux thì truyền
    String actionType;      // "install" hoặc "config"
    Map<String, Object> extraVars;

    String ansibleUser;   // optional; mặc định "Administrator"
    Integer ansiblePort;  // optional; mặc định 5985
}
