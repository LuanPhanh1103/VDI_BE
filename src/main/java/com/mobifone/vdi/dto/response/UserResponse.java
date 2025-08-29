package com.mobifone.vdi.dto.response;

import com.mobifone.vdi.entity.enumeration.Gender;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String username;
    String firstName;
    String lastName;
    String email;
    Gender gender;
    String age;
    Set<RoleResponse> roles;
    Set<VirtualDesktopResponse> virtualDesktops;
}
