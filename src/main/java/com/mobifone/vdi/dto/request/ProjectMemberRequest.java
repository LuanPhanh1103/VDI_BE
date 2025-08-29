package com.mobifone.vdi.dto.request;

import com.mobifone.vdi.entity.enumeration.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProjectMemberRequest {
    @NotNull
    String projectId;

    @NotNull
    String userId;

    @NotNull
    ProjectRole projectRole; // ADMIN/MEMBER (OWNER set khi tạo project)
}
