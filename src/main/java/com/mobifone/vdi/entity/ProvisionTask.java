package com.mobifone.vdi.entity;

import com.mobifone.vdi.entity.enumeration.TaskStatus;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class ProvisionTask extends AbstractAuditingEntity<String> implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name="task_id", unique = true, nullable = false, length = 64)
    String taskId;                                      // UUID string

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    TaskStatus status;                                  // PROVISIONING | SUCCESS | FAILED

    @Column(columnDefinition = "LONGTEXT")
    String errorMessage;

    @Column(name="instance_floating_pairs", columnDefinition = "LONGTEXT")
    String instanceFloatingPairs;                       // JSON: [{ "instance_id": "...", "floating_ip": "..." }]
}
