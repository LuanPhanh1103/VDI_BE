package com.mobifone.vdi.service;

import java.util.List;
import java.util.Set;

import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.mobifone.vdi.dto.request.PermissionRequest;
import com.mobifone.vdi.dto.response.PermissionResponse;
import com.mobifone.vdi.entity.Permission;
import com.mobifone.vdi.mapper.PermissionMapper;
import com.mobifone.vdi.repository.PermissionRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionService {
    PermissionRepository permissionRepository;
    PermissionMapper permissionMapper;

    @PreAuthorize("hasRole('create_permission')")
    public PermissionResponse create(PermissionRequest request) {
        if (permissionRepository.existsById(request.getName())){
            throw new AppException(ErrorCode.PERMISSION_EXITED);
        }
        Permission permission = permissionMapper.toPermission(request);
        permission = permissionRepository.save(permission);
        return permissionMapper.toPermissionResponse(permission);
    }

    public List<Permission> findAllById(Set<String> ids){
        return permissionRepository.findAllById(ids);
    }

//    @PreAuthorize("hasRole('get_all_permissions')")
//    public PagedResponse<PermissionResponse> getAll(int page, int size) {
//        int currentPage = Math.max(page, 1);   // 1-based
//        int pageSize    = Math.max(size, 1);
//
//        Pageable pageable = PageRequest.of(currentPage - 1, pageSize); // chuyển về 0-based cho Spring Data
//        Page<Permission> permissions = permissionRepository.findAll(pageable);
//
//        List<PermissionResponse> data = permissions.getContent()
//                .stream()
//                .map(permissionMapper::toPermissionResponse)
//                .toList();
//
//        return PagedResponse.<PermissionResponse>builder()
//                .data(data)
//                .page(currentPage)
//                .size(pageSize)
//                .totalElements(permissions.getTotalElements())
//                .totalPages(permissions.getTotalPages())
//                .build();
//    }

//    @PreAuthorize("hasRole('get_permissions')")
    public PagedResponse<PermissionResponse> getAll(int page, int size, String search) {
        int currentPage = Math.max(page, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, size);

        Page<Permission> pageData;
        String kw = search == null ? "" : search.trim();
        if (!kw.isBlank()) {
            pageData = permissionRepository
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(kw, kw, pageable);
        } else {
            pageData = permissionRepository.findAll(pageable);
        }

        var data = pageData.getContent().stream()
                .map(permissionMapper::toPermissionResponse)
                .toList();

        return PagedResponse.<PermissionResponse>builder()
                .data(data)
                .page(currentPage)
                .size(size)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

    // Nếu muốn giữ lại hàm cũ không có search
    public PagedResponse<PermissionResponse> getAll(int page, int size) {
        return getAll(page, size, "");
    }



    @PreAuthorize("hasRole('delete_permission')")
    public void delete(String permission) {
        permissionRepository.deleteById(permission);
    }
}
