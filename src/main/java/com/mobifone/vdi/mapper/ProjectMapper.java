package com.mobifone.vdi.mapper;

import com.mobifone.vdi.dto.request.ProjectRequest;
import com.mobifone.vdi.dto.response.ProjectResponse;
import com.mobifone.vdi.dto.response.ProjectUserResponse;
import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.entity.UserProject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", uses = { VirtualDesktopMapper.class })
public interface ProjectMapper {

    // --------- Entity -> Entity ----------
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "virtualDesktops", ignore = true)
    Project toEntity(ProjectRequest req);

    // --------- Entity -> Response ----------
    // owner: User -> ProjectUserResponse (MapStruct tự map bằng method bên dưới)
    @Mapping(target = "owner", source = "owner")
    // members: Set<UserProject> -> List<ProjectUserResponse> qua @Named helper
    @Mapping(target = "members", source = "members", qualifiedByName = "mapMembers")
    ProjectResponse toResponse(Project p);

    // User -> ProjectUserResponse (không có roles, vẫn giữ virtualDesktops)
    ProjectUserResponse toProjectUserResponse(User u);

    // ===== Helper method: Set<UserProject> -> List<ProjectUserResponse> =====
    @Named("mapMembers")
    default java.util.List<ProjectUserResponse> mapMembers(java.util.Set<UserProject> members) {
        if (members == null || members.isEmpty()) return java.util.List.of();
        return members.stream()
                .map(UserProject::getUser)               // lấy User trong UserProject
                .map(this::toProjectUserResponse)         // map sang ProjectUserResponse
                .toList();
    }
}