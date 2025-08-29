package com.mobifone.vdi.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class AnsibleJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String jobId;
    String targetIps; // lưu dạng "ip1,ip2,ip3"
    String apps;      // lưu dạng "office,chrome"
    String winVersion;
    String linuxVersion;
    String actionType; // install/config
    String status;     // PENDING, RUNNING, SUCCESS, FAILED
    String logPath;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
