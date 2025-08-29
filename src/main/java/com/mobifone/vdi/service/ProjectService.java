package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.ProjectMemberRequest;
import com.mobifone.vdi.dto.request.ProjectRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.ProjectResponse;
import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.entity.UserProject;
import com.mobifone.vdi.entity.enumeration.ProjectRole;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.mapper.ProjectMapper;
import com.mobifone.vdi.repository.ProjectRepository;
import com.mobifone.vdi.repository.UserProjectRepository;
import com.mobifone.vdi.repository.VirtualDesktopRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProjectService {
    ProjectRepository projectRepo;
    UserProjectRepository userProjectRepo;
    VirtualDesktopRepository virtualDesktopRepository;
    UserService userService;
    ProjectMapper projectMapper;

    @PreAuthorize("hasRole('create_project')")
    @Transactional
    public ProjectResponse create(ProjectRequest req) {
        if (projectRepo.existsByName(req.getName())) throw new AppException(ErrorCode.PROJECT_EXISTED);

        var owner = userService.findUserById(req.getOwnerId());
        var project = projectMapper.toEntity(req);
        project.setOwner(owner);
        project = projectRepo.save(project);

        userProjectRepo.save(UserProject.builder()
                .project(project)
                .user(owner)
                .projectRole(ProjectRole.OWNER)
                .build());

        var full = projectRepo.findById(project.getId()).orElseThrow();

        return projectMapper.toResponse(full);
    }

    @PreAuthorize("hasRole('create_projects_member')")
    @Transactional
    public void addMember(ProjectMemberRequest req) {
        var project = projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
        var user = userService.findUserById(req.getUserId());
        if (userProjectRepo.existsByUser_IdAndProject_Id(user.getId(), project.getId())) return;

        userProjectRepo.save(UserProject.builder()
                .project(project)
                .user(user)
                .projectRole(req.getProjectRole())
                .build());
    }

    @PreAuthorize("hasRole('delete_projects_member')")
    @Transactional
    public void removeMember(String projectId, String userId) {
        // 1. Xoá user_project (nếu không phải OWNER)
        var toRemove = userProjectRepo.findAllByProject_Id(projectId).stream()
                .filter(up -> up.getUser().getId().equals(userId) && up.getProjectRole() != ProjectRole.OWNER)
                .toList();
        userProjectRepo.deleteAll(toRemove);

        // 2. Unassign tất cả VDI thuộc project đó mà đang gán cho user đó
        var vdis = virtualDesktopRepository.findAllByProject_IdAndUser_Id(projectId, userId);
        for (var vd : vdis) {
            vd.setUser(null); // gỡ gán người dùng khỏi VDI
        }
        virtualDesktopRepository.saveAll(vdis);
    }

//    @PreAuthorize("hasRole('get_project')")
//    @Transactional(readOnly = true)
//    public PagedResponse<ProjectResponse> getAll(int page, int size) {
//        int currentPage = Math.max(page, 1);
//        int pageSize = Math.max(size, 1);
//
//        Pageable pageable = PageRequest.of(currentPage - 1, pageSize); // Spring Data dùng 0-based
//        Page<Project> pageData = projectRepo.findAll(pageable);
//
//        List<ProjectResponse> data = pageData.getContent()
//                .stream()
//                .map(projectMapper::toResponse)
//                .toList();
//
//        return PagedResponse.<ProjectResponse>builder()
//                .data(data)
//                .page(currentPage)                 // 1-based trả về cho client
//                .size(pageSize)
//                .totalElements(pageData.getTotalElements())
//                .totalPages(pageData.getTotalPages())
//                .build();
//    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> {
            var au = a.getAuthority();
            return "admin".equalsIgnoreCase(au) || "ROLE_admin".equalsIgnoreCase(au);
        });
    }

    @PreAuthorize("hasRole('get_projects')")
    @Transactional(readOnly = true)
    public PagedResponse<ProjectResponse> getAll(int page, int size, String search) {
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);
        String kw = (search == null) ? "" : search.trim();

        Page<Project> pageData;

        if (isAdmin()) {
            pageData = kw.isBlank()
                    ? projectRepo.findAllBy(pageable)
                    : projectRepo.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(kw, kw, pageable);
        } else {
            String uid = userService.getMyInfo().getId();
            if (!projectRepo.existsByOwner_Id(uid)) {
                // Member không được xem
                throw new AppException(ErrorCode.PERMISSION_DENIED);
            }
            pageData = projectRepo.findOwnedProjects(uid, kw, pageable);
        }

        var data = pageData.getContent().stream()
                .map(projectMapper::toResponse)
                .toList();

        return PagedResponse.<ProjectResponse>builder()
                .data(data)
                .page(currentPage)
                .size(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

//    // Giữ hàm cũ nếu nơi khác đang gọi
//    @PreAuthorize("hasRole('get_projects')")
//    @Transactional(readOnly = true)
//    public PagedResponse<ProjectResponse> getAll(int page, int size) {
//        return getAll(page, size, "");
//    }


    @PreAuthorize("hasRole('get_project')")
    @Transactional(readOnly = true)
    public ProjectResponse getById(String id) {
        var project = projectRepo.findDetailById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
        return projectMapper.toResponse(project);
    }


    @PreAuthorize("hasRole('delete_project')")
    @Transactional
    public void softDelete(String id) {
        var project = projectRepo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
        project.setIsDeleted(1L);
        projectRepo.save(project);
    }
}
