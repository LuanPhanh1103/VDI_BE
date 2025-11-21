package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.ProjectMemberRequest;
import com.mobifone.vdi.dto.request.ProjectRequest;
import com.mobifone.vdi.dto.response.InstanceResponse;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.ProjectResponse;
import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.entity.VirtualDesktop;
import com.mobifone.vdi.entity.enumeration.ProjectRole;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.mapper.ProjectMapper;
import com.mobifone.vdi.repository.ProjectRepository;
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

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProjectService {
    ProjectRepository projectRepo;         // OK (cùng domain)
    ProjectMapper projectMapper;
    UserService userService;               // cross-domain qua service
    UserProjectService userProjectService; // NEW
    VirtualDesktopService virtualDesktopService; // cross-domain qua service
    OpenStackService openStackService;     // gọi API hạ tầng

    @PreAuthorize("hasRole('create_project')")
    @Transactional
    public ProjectResponse create(ProjectRequest req) {
        if (projectRepo.existsByName(req.getName())) throw new AppException(ErrorCode.PROJECT_EXISTED);

        var owner = userService.findUserById(req.getOwnerId()); // lấy User qua service
        var project = projectMapper.toEntity(req);
        project.setOwner(owner);
        project = projectRepo.save(project);

        // tạo liên kết OWNER qua service
        userProjectService.addMember(project.getId(), owner, ProjectRole.OWNER);

        var full = projectRepo.findById(project.getId()).orElseThrow();
        return projectMapper.toResponse(full);
    }

    @PreAuthorize("hasRole('create_projects_member')")
    @Transactional
    public void addMember(ProjectMemberRequest req) {
        var user = userService.findUserById(req.getUserId());
        projectRepo.findById(req.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
        userProjectService.addMember(req.getProjectId(), user, req.getProjectRole());
    }

    @PreAuthorize("hasRole('delete_projects_member')")
    @Transactional
    public void removeMember(String projectId, String userId) {
        userProjectService.removeMember(projectId, userId);
        virtualDesktopService.unassignUserFromProjectVDIs(projectId, userId);
    }

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


    @PreAuthorize("hasRole('get_project')")
    @Transactional(readOnly = true)
    public ProjectResponse getById(String id) {
        var project = projectRepo.findDetailById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
        return projectMapper.toResponse(project);
    }


    /**
     * Gọi destroy infra; chờ event OK sẽ cascade qua ProvisionPersistService → gọi lại ProjectService.cascadeMarkProjectDeleted(...)
     */
    @PreAuthorize("hasRole('delete_project')")
    @Transactional(readOnly = true)
    public InstanceResponse softDelete(String projectId, String region) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));

        String ownerUserId = project.getOwner().getId();

        // 🔍 Lấy 1 VDI bất kỳ để biết infraId
        String infraId = virtualDesktopService.findAnyByProject(projectId, region)
                .map(VirtualDesktop::getInfraId)
                .orElseThrow(() -> new AppException(ErrorCode.ORCHESTRATOR_SERVICE_IP_PUBLIC_ORG));

        // 🔥 Destroy đúng infraId
        return openStackService.requestDestroyInfra(ownerUserId, projectId, infraId, region);
    }

    // (nếu cần dùng bên khác, thêm)
    @Transactional(readOnly = true)
    public Project loadEntity(String id) {
        return projectRepo.findById(id).orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
    }
}
