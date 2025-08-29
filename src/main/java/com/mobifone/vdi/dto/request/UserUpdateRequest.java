package com.mobifone.vdi.dto.request;

import com.mobifone.vdi.entity.enumeration.Gender;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String firstName;
    String lastName;
    String email;
    Gender gender;
    Long age;

    List<String> roles;
}
