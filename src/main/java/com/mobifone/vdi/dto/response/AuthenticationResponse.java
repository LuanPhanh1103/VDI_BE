package com.mobifone.vdi.dto.response;

import com.mobifone.vdi.entity.Permission;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthenticationResponse {
    String token;
    String userId;
    String username;
    String firstName;
    String lastName;
    String email;
//    Set<Permission> permissions;
    Set<RoleResponse> roles;
    boolean authenticated;
}
