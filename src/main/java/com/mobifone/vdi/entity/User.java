package com.mobifone.vdi.entity;

import com.mobifone.vdi.entity.enumeration.Gender;
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
public class User extends AbstractAuditingEntity<String> implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "username", unique = true, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_unicode_ci")
    String username;

    String password;
    String firstName;
    String lastName;
    String email;

    @Enumerated(EnumType.STRING)
    Gender gender;

    String age;

    @ManyToMany
    Set<Role> roles;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<VirtualDesktop> virtualDesktops;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    Set<UserProject> userProjects = new HashSet<>();
}
