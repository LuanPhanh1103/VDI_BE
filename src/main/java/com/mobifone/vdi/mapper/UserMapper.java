package com.mobifone.vdi.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.mobifone.vdi.dto.request.UserCreationRequest;
import com.mobifone.vdi.dto.request.UserUpdateRequest;
import com.mobifone.vdi.dto.response.UserResponse;
import com.mobifone.vdi.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "password", ignore = true)
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
