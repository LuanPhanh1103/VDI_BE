package com.mobifone.vdi.entity;

import jakarta.persistence.*;
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
public class ApiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String endpoint;

    String httpMethod;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    String requestHeader;

    String requestParams;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    String requestBody;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    String responseBody;

    String responseStatus;

    LocalDateTime startTime;

    LocalDateTime endTime;

    String ipAddress;
}
