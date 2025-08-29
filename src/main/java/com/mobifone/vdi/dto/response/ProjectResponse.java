package com.mobifone.vdi.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProjectResponse {
    String id;
    String name;
    String description;
//    String ownerId;
//    List<String> memberIds; // gồm cả owner nếu bạn muốn
    // Thay ownerId -> owner (full info user, không có roles)
    ProjectUserResponse owner;

    // Thay memberIds -> members (full info user, không có roles)
    List<ProjectUserResponse> members;

}
