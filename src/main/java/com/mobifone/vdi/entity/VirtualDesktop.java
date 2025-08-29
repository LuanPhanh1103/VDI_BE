package com.mobifone.vdi.entity;

import com.mobifone.vdi.entity.enumeration.HasGPU;
import com.mobifone.vdi.entity.enumeration.VolumeType;
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
public class VirtualDesktop extends AbstractAuditingEntity<String> implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String ipLocal;
    String ipPublic;
    String password;
    String name;
    String portLocal;
    String portPublic;
    String CPU;
    String GPU;
    String RAM;
    String idInstance;
    String typeVirtualDesktop;

    @Enumerated(EnumType.STRING)
    VolumeType volumeType;

    String volumeSize;

    @Enumerated(EnumType.STRING)
    HasGPU hasGPU;

    @ManyToOne
    @JoinColumn(name = "project_id")
    Project project;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
