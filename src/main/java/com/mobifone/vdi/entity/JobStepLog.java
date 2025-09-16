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
@Table(name = "job_step_log")
public class JobStepLog {
    @Id
    String id;

    String jobId;
    String vdId;
    String step;                     // ví dụ: create_instance, create_nat, install:chrome ...

    @Column(length = 16)
    String status;                   // INFO|SUCCESS|FAILED

    @Column(columnDefinition = "TEXT")  // <--- CHO PHÉP LÂU DÒNG
    String detail;

    LocalDateTime createdAt;
}
