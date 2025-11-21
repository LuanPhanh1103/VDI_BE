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

    String region;
    String ipLocal;
    String ipPublic;
    String password;
    String name;
    String portLocal;

    @Column(unique = true)
    String portPublic;

    @Column(unique = true)
    String portWinRmPublic;

    String cpu;
    String gpu;
    String ram;
    String idInstance;
    String typeVirtualDesktop;

    Boolean isDomainController = false;
    String domainName;
    String domainOu;
    String domainAccountUsername;
    String domainAccountPassword;

    @Enumerated(EnumType.STRING)
    VolumeType volumeType;

    String volumeSize;

    @Enumerated(EnumType.STRING)
    HasGPU hasGPU;

    String status;                 // CREATED|NAT_ERROR|APP_ERROR|READY

    Boolean winRmDisabled;
    String jobId;

    @Lob
    String ansiblePlan;

    @Column(name = "infra_id")
    String infraId;

    @ManyToOne
    @JoinColumn(name = "project_id")
    Project project;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}
