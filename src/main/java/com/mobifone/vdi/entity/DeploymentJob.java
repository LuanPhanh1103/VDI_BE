package com.mobifone.vdi.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Entity
@Table(name = "deployment_job")
public class DeploymentJob {
    @Id
    String id;

    String mode;             // personal|organization|add-resource
    String requesterId;

    @Column(length = 16)
    String status;           // PENDING|RUNNING|SUCCESS|FAILED|PARTIAL_FAILED

    Integer totalVms;
    Integer successVms;
    Integer failedVms;

    @Column(columnDefinition = "LONGTEXT")
    String message;

    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
