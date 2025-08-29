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

    // ===== Admin: xem tất cả (giữ lại cho tương thích) =====
//    @PreAuthorize("hasRole('admin') or hasRole('get_all_VDI')")
//    @Transactional(readOnly = true)
//    public PagedResponse<VirtualDesktopResponse> getAllVirtualDesktops(int page, int size) {
//        int currentPage = Math.max(page, 1);
//        Pageable pageable = PageRequest.of(currentPage - 1, size);
//
//        Page<VirtualDesktop> pageData = virtualDesktopRepository.findAll(pageable);
//        var data = pageData.getContent().stream()
//                .map(virtualDesktopMapper::toVirtualDesktopResponse)
//                .toList();
//
//        return PagedResponse.<VirtualDesktopResponse>builder()
//                .data(data)
//                .page(currentPage)
//                .size(size)
//                .totalElements(pageData.getTotalElements())
//                .totalPages(pageData.getTotalPages())
//                .build();
//    }


//    // ===== Legacy: theo userId (nên deprecate ở FE) =====
//    @PreAuthorize("hasRole('admin') or #userId == principal?.name")
//    @Transactional(readOnly = true)
//    public List<VirtualDesktopResponse> getAllVirtualDesktopsByUserId(String userId) {
//        return virtualDesktopRepository.findAllByUserId(userId).stream()
//                .map(virtualDesktopMapper::toVirtualDesktopResponse).toList();
//    }

    // ===== Resolve theo quyền hiện tại (Admin/Owner/Member) – có thể lọc theo projectId =====
//    @PreAuthorize("hasRole('get_virtualDesktops')")
//    @Transactional(readOnly = true)
//    public PagedResponse<VirtualDesktopResponse> getVDIsForCurrentUser(
//            String projectId, String search, int page, int size) {
//
//        final String kw = search == null ? "" : search.trim().toLowerCase();
//
//        List<VirtualDesktop> all; // làm việc trên entity trước, rồi mới map DTO
//
//        if (isAdmin()) {
//            all = (projectId == null)
//                    ? virtualDesktopRepository.findAll()
//                    : virtualDesktopRepository.findAllByProject_Id(projectId);
//
//        } else {
//            String uid = currentUserId();
//
//            if (projectId != null) {
//                var project = projectRepository.findById(projectId)
//                        .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));
//
//                if (project.getOwner().getId().equals(uid)) {
//                    all = virtualDesktopRepository.findAllByProject_Id(projectId);
//                } else if (userProjectRepository.existsByUser_IdAndProject_Id(uid, projectId)) {
//                    all = virtualDesktopRepository.findAllByProject_IdAndUser_Id(projectId, uid);
//                } else {
//                    all = List.of();
//                }
//            } else {
//                var myOwnerProjects = projectRepository.findAllByOwner_Id(uid);
//                if (!myOwnerProjects.isEmpty()) {
//                    all = myOwnerProjects.stream()
//                            .flatMap(p -> virtualDesktopRepository.findAllByProject_Id(p.getId()).stream())
//                            .toList();
//                } else {
//                    all = virtualDesktopRepository.findAllByUserId(uid);
//                }
//            }
//        }
//
//        // --- LỌC THEO KEYWORD (name, ipLocal, ipPublic), không phân biệt hoa/thường ---
//        if (!kw.isBlank()) {
//            all = all.stream()
//                    .filter(vd -> containsIgnoreCase(vd.getName(), kw)
//                            || containsIgnoreCase(vd.getIpLocal(), kw)
//                            || containsIgnoreCase(vd.getIpPublic(), kw))
//                    .toList();
//        }
//
//        // --- Phân trang thủ công 1-based ---
//        int currentPage = Math.max(page, 1);
//        int pageSize = Math.max(size, 1);
//        int fromIndex = (currentPage - 1) * pageSize;
//
//        int total = all.size();
//        int totalPages = (int) Math.ceil((double) total / pageSize);
//
//        List<VirtualDesktopResponse> paged;
//        if (fromIndex >= total) {
//            paged = List.of();
//        } else {
//            int toIndex = Math.min(fromIndex + pageSize, total);
//            paged = all.subList(fromIndex, toIndex).stream()
//                    .map(virtualDesktopMapper::toVirtualDesktopResponse)
//                    .toList();
//        }
//
//        return PagedResponse.<VirtualDesktopResponse>builder()
//                .data(paged)
//                .page(currentPage)
//                .size(pageSize)
//                .totalElements(total)
//                .totalPages(totalPages)
//                .build();
//    }
//
//    // helper an toàn null
//    private boolean containsIgnoreCase(String field, String kwLower) {
//        return field != null && field.toLowerCase().contains(kwLower);
//    }

    @PreAuthorize("hasRole('get_virtualDesktops')") // giữ permission chung
    @Transactional(readOnly = true)
    public PagedResponse<VirtualDesktopResponse> getVDIsForCurrentUser(
            String projectId, String search, int page, int size) {

        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize);
        String kw = (search == null) ? "" : search.trim();

        Page<VirtualDesktop> pageData;

        if (isAdmin()) {
            // ADMIN: tất cả
            pageData = virtualDesktopRepository.searchAllVDIs(projectId, kw, pageable);

        } else {
            String uid = currentUserId();
            // xác định có phải OWNER của ít nhất 1 project
            List<String> ownedProjectIds = projectRepository.findIdsByOwner(uid);

            if (!ownedProjectIds.isEmpty()) {
                // OWNER: tất cả VDI thuộc các project do mình làm chủ
                pageData = virtualDesktopRepository.searchVDIsInProjects(ownedProjectIds, projectId, kw, pageable);
            } else {
                // MEMBER: chỉ VDI gán cho chính mình
                pageData = virtualDesktopRepository.searchAssignedVDIs(uid, projectId, kw, pageable);
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
}
