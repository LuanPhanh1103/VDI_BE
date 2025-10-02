package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.VirtualDesktopRequest;
import com.mobifone.vdi.dto.request.VirtualDesktopUpdateRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.VirtualDesktopResponse;
import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.entity.VirtualDesktop;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.mapper.VirtualDesktopMapper;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VirtualDesktopService {
    VirtualDesktopRepository virtualDesktopRepository;
    VirtualDesktopMapper virtualDesktopMapper;
    UserService userService;
    ProjectRepository projectRepository;
    UserProjectRepository userProjectRepository;

    // ADDED: lấy DC theo project (+ region nếu cần)
    @Transactional(readOnly = true)
    public Optional<VirtualDesktop> findDomainController(String projectId, String region) {
        if (region == null || region.isBlank())
            return virtualDesktopRepository.findFirstByProject_IdAndIsDomainControllerTrue(projectId);
        return virtualDesktopRepository.findFirstByProject_IdAndRegionAndIsDomainControllerTrue(projectId, region);
    }

    // === DÙNG CHO SERVICE KHÁC ===
    @Transactional(readOnly = true)
    public boolean isPortPublicUsed(int port) {
        return virtualDesktopRepository.existsByPortPublic(String.valueOf(port));
    }

    @Transactional
    public void unassignUserFromProjectVDIs(String projectId, String userId) {
        var vdis = virtualDesktopRepository.findAllByProject_IdAndUser_Id(projectId, userId);
        for (var vd : vdis) vd.setUser(null);
        virtualDesktopRepository.saveAll(vdis);
    }

    @Transactional
    public void markAllDeletedByProject(String projectId) {
        var vdis = virtualDesktopRepository.findAllByProject_Id(projectId);
        for (var vd : vdis) vd.setIsDeleted(1L);
        virtualDesktopRepository.saveAll(vdis);
    }

    // Cho Orchestrator dùng, tránh dùng repo trực tiếp
    @Transactional
    public VirtualDesktop save(VirtualDesktop vd) { return virtualDesktopRepository.save(vd); }

    @Transactional(readOnly = true)
    public Optional<VirtualDesktop> findById(String id) { return virtualDesktopRepository.findById(id); }


    // ===== Helpers =====
    private String currentUserId() {
        return userService.getMyInfo().getId();
    }

    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> {
                    var au = a.getAuthority();
                    return "admin".equalsIgnoreCase(au) || "ROLE_admin".equalsIgnoreCase(au);
                });
    }

    // ===== Create VDI: chỉ cho phép nếu user là member của project =====
    @PreAuthorize("hasRole('create_virtualDesktop') or hasRole('admin')")
    @Transactional
    public VirtualDesktopResponse createVirtualDesktop(VirtualDesktopRequest request) {
        if (virtualDesktopRepository.existsByName(request.getName()))
            throw new AppException(ErrorCode.VIRTUAL_DESKTOP_EXITED);

        User user = userService.findUserById(request.getUserId());
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));

        if (!userProjectRepository.existsByUser_IdAndProject_Id(user.getId(), project.getId()))
            throw new AppException(ErrorCode.PERMISSION_DENIED);

        VirtualDesktop vd = virtualDesktopMapper.toVirtualDesktop(request);
        vd.setUser(user);
        vd.setProject(project);
        vd = virtualDesktopRepository.save(vd);

        return virtualDesktopMapper.toVirtualDesktopResponse(vd);
    }

    @PreAuthorize("hasRole('get_virtualDesktops')")
    @Transactional(readOnly = true)
    public PagedResponse<VirtualDesktopResponse> getVDIsForCurrentUser(
            String projectId, String search, int page, int size, String region) {   // <<< NEW param

        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);
        String kw = (search == null) ? "" : search.trim();
        String r = (region == null || region.isBlank()) ? null : region;

        Page<VirtualDesktop> pageData;

        if (isAdmin()) {
            pageData = virtualDesktopRepository.searchAllVDIs(projectId, kw, r, pageable);
        } else {
            String uid = currentUserId();
            List<String> ownedProjectIds = projectRepository.findIdsByOwner(uid);
            if (!ownedProjectIds.isEmpty()) {
                pageData = virtualDesktopRepository.searchVDIsInProjects(ownedProjectIds, projectId, kw, r, pageable);
            } else {
                pageData = virtualDesktopRepository.searchAssignedVDIs(uid, projectId, kw, r, pageable);
            }
        }

        var data = pageData.getContent().stream()
                .map(virtualDesktopMapper::toVirtualDesktopResponse)
                .toList();

        return PagedResponse.<VirtualDesktopResponse>builder()
                .data(data)
                .page(currentPage)
                .size(pageSize)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

    // ===== Get by ID – kiểm tra phạm vi =====
    @PreAuthorize("hasRole('get_virtualDesktop') or @projectSecurity.canReadVDI(#id, authentication)")
    @Transactional(readOnly = true)
    public VirtualDesktopResponse getVirtualDesktopInfo(String id) {
        var vd = virtualDesktopRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIRTUAL_DESKTOP_NOT_EXITED));
        return virtualDesktopMapper.toVirtualDesktopResponse(vd);
    }

    // ===== Update – owner/admin; member chỉ khi là assignee (tùy policy) =====
    @PreAuthorize("hasRole('update_virtualDesktop') or @projectSecurity.canUpdateVDI(#id, authentication)")
    @Transactional
    public VirtualDesktopResponse updateVirtualDesktop(String id, VirtualDesktopUpdateRequest request) {
        VirtualDesktop vd = virtualDesktopRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIRTUAL_DESKTOP_NOT_EXITED));

        if (request.getUserId() != null) {
            User user = userService.findUserById(request.getUserId());
            vd.setUser(user);
        }
        virtualDesktopMapper.updateVirtualDesktop(vd, request);

        return virtualDesktopMapper.toVirtualDesktopResponse(virtualDesktopRepository.save(vd));
    }

    // ===== Delete – thường chỉ owner/admin =====
    @PreAuthorize("hasRole('admin') or hasRole('delete_virtualDesktop')")
    @Transactional
    public void deleteVirtualDesktop(String id) {
        VirtualDesktop vd = virtualDesktopRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIRTUAL_DESKTOP_NOT_EXITED));
        vd.setIsDeleted(1L);
        virtualDesktopRepository.save(vd);
    }

    @Transactional(readOnly = true)
    public Optional<VirtualDesktop> findByIdInstanceOpt(String idInstance) {
        return virtualDesktopRepository.findByIdInstance(idInstance);
    }

    public void deleteVirtualDesktopByIdInstance(String id) {
        VirtualDesktop vd = virtualDesktopRepository.findByIdInstance(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIRTUAL_DESKTOP_NOT_EXITED));
        vd.setIsDeleted(1L);
        virtualDesktopRepository.save(vd);
    }

    @Transactional(readOnly = true)
    public List<VirtualDesktop> findByJobId(String jobId) {
        return virtualDesktopRepository.findByJobId(jobId);
    }

    /** Đã có VDI dùng port_winrm_public này chưa? */
    public boolean isPortWinRmPublicUsed(int port) {
        return virtualDesktopRepository.existsByPortWinRmPublic(String.valueOf(port));
    }

    /** Bất kỳ port public nào (RDP NAT hoặc WinRM NAT) đã dùng chưa? */
    public boolean isAnyPublicPortUsed(int port) {
        return isPortPublicUsed(port) || isPortWinRmPublicUsed(port);
    }

}
