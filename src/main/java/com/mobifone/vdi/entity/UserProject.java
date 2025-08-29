package com.mobifone.vdi.entity;

import com.mobifone.vdi.entity.enumeration.ProjectRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Where;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Where(clause = "is_deleted = 0")
public class UserProject extends AbstractAuditingEntity<String> implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne @JoinColumn(name = "user_id")
    User user;

    @ManyToOne @JoinColumn(name = "project_id")
    Project project;

    @Enumerated(EnumType.STRING)
    ProjectRole projectRole;
}
