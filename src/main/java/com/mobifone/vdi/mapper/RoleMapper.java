package com.mobifone.vdi.mapper;

import com.mobifone.vdi.dto.request.RoleRequest;
import com.mobifone.vdi.dto.request.RoleUpdateRequest;
import com.mobifone.vdi.dto.response.RoleResponse;
import com.mobifone.vdi.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    @Mapping(target = "permissions", ignore = true)
    Role toRole(RoleRequest request);

    RoleResponse toRoleResponse(Role role);

    @Mapping(target = "permissions", ignore = true)
    void updateRole(@MappingTarget Role role, RoleUpdateRequest request);
}
