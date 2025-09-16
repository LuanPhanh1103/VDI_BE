package com.mobifone.vdi.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Entity
@Table(name = "app_deployment")
public class AppDeployment {
    @Id
    String id;

    String vdId;
    String jobId;

    String appCode;          // "chrome", "office", "change_password"
    String actionType;       // INSTALL|CONFIG

    String status;           // PENDING|RUNNING|SUCCESS|FAILED
    Integer retryCount;

    @Lob
    String logPath;

    LocalDateTime startedAt;
    LocalDateTime finishedAt;
}
