package com.mobifone.vdi.mapper;

import com.mobifone.vdi.dto.request.VirtualDesktopRequest;
import com.mobifone.vdi.dto.request.VirtualDesktopUpdateRequest;
import com.mobifone.vdi.dto.response.ProjectBrief;
import com.mobifone.vdi.dto.response.UserBrief;
import com.mobifone.vdi.dto.response.VirtualDesktopResponse;
import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.entity.VirtualDesktop;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface VirtualDesktopMapper {
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "project", ignore = true)
    VirtualDesktop toVirtualDesktop(VirtualDesktopRequest request);

    @Mapping(target = "user", expression = "java(toUserBrief(entity.getUser()))")
    @Mapping(target = "project", expression = "java(toProjectBrief(entity.getProject()))")
    VirtualDesktopResponse toVirtualDesktopResponse(VirtualDesktop entity);

//    // mapping ngược nếu cần
//    VirtualDesktop toVirtualDesktop(VirtualDesktopRequest req);

    // --- helpers ---
    default UserBrief toUserBrief(User u) {
        if (u == null) return null;
        return UserBrief.builder()
                .id(u.getId())
                .username(u.getUsername())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .build();
    }

    default ProjectBrief toProjectBrief(Project p) {
        if (p == null) return null;
        return ProjectBrief.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .build();
    }

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "ipLocal", ignore = true)
    @Mapping(target = "ipPublic", ignore = true)
    @Mapping(target = "portLocal", ignore = true)
    @Mapping(target = "portPublic", ignore = true)
    @Mapping(target = "CPU", ignore = true)
    @Mapping(target = "GPU", ignore = true)
    @Mapping(target = "RAM", ignore = true)
    @Mapping(target = "volumeType", ignore = true)
    @Mapping(target = "volumeSize", ignore = true)
    @Mapping(target = "idInstance", ignore = true)
    @Mapping(target = "hasGPU", ignore = true)
    @Mapping(target = "typeVirtualDesktop", ignore = true)
    void updateVirtualDesktop(@MappingTarget VirtualDesktop entity, VirtualDesktopUpdateRequest request);
}
