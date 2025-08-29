package com.mobifone.vdi.mapper;

import org.mapstruct.Mapper;

import com.mobifone.vdi.dto.request.PermissionRequest;
import com.mobifone.vdi.dto.response.PermissionResponse;
import com.mobifone.vdi.entity.Permission;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);

    PermissionResponse toPermissionResponse(Permission permission);
}
