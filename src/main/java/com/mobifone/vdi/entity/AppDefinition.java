package com.mobifone.vdi.entity;

import com.mobifone.vdi.entity.enumeration.ActionType;
import com.mobifone.vdi.utils.ListStringJsonConverter;
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
    private String code;     // "chrome", "office", "join_domain"

    @Column(nullable = false, length = 128)
    String name;                       // "Google Chrome"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ActionType actionType;             // INSTALL | CONFIG

    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    List<String> winVersion;           // ["10","11","2019","2022"]

    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    List<String> linuxVersion;         // ["ubuntu-22.04","debian-12",...]

    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    List<String> extraVars;     // {"account_name":"Administrator", ...}

    @Builder.Default
    Boolean enabled = true;
}
