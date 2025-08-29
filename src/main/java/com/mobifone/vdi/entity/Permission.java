package com.mobifone.vdi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Permission {
    @Id
    @Column(name = "name", unique = true, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin")
    String name;

    String description;
}
