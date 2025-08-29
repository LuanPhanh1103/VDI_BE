package com.mobifone.vdi.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Where;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Where(clause = "is_deleted = 0")
public class Project extends AbstractAuditingEntity<String> implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(unique = true)
    String name;

    String description;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    User owner;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    Set<UserProject> members = new HashSet<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    Set<VirtualDesktop> virtualDesktops = new HashSet<>();
}
