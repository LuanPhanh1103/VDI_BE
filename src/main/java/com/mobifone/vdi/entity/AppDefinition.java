package com.mobifone.vdi.entity;

import com.mobifone.vdi.entity.enumeration.ActionType;
import com.mobifone.vdi.entity.enumeration.RunType;
import com.mobifone.vdi.utils.ListStringJsonConverter;
import com.mobifone.vdi.utils.MapJsonConverter;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class AppDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false, length = 128)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ActionType actionType; // INSTALL | CONFIG

    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    List<String> winVersion;

    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    List<String> linuxVersion;

    // ✅ giữ 1 bộ biến mặc định duy nhất (trước là defaultExtraVars)
    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    List<String> requiredVars;         // vd: ["domain_name","dsrm_password"]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    RunType runType;                 // ROLE | PLAYBOOK

    // chọn 1 trong 2 tuỳ runType
    String roleName;                 // nếu runType=ROLE
    String playbookPath;             // nếu runType=PLAYBOOK

    @Builder.Default
    Boolean enabled = true;
}