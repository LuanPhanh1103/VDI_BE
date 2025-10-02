package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.RoleRequest;
import com.mobifone.vdi.dto.request.RoleUpdateRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.RoleResponse;
import com.mobifone.vdi.entity.Role;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.mapper.RoleMapper;
import com.mobifone.vdi.repository.RoleRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleService {
    RoleRepository roleRepository;
    PermissionService permissionService;
    RoleMapper roleMapper;

    @PreAuthorize("hasRole('create_role')")
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsById(request.getName())){
            throw new AppException(ErrorCode.ROLE_EXITED);
        }
        var role = roleMapper.toRole(request);

        var permissions = permissionService.findAllById(request.getPermissions());
        role.setPermissions(new HashSet<>(permissions));

        role = roleRepository.save(role);
        return roleMapper.toRoleResponse(role);
    }

    @PreAuthorize("hasRole('update_role')")
    public RoleResponse updateRole(String roleName, RoleUpdateRequest request) {
        Role role = roleRepository.findById(roleName).orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXITED));

        roleMapper.updateRole(role, request);

        log.info("listttttttt role: {}", role.getPermissions());

        var permissions = permissionService.findAllById(request.getPermissions());

        role.setPermissions(new HashSet<>(permissions));

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    @PreAuthorize("hasRole('get_roles')")
    public PagedResponse<RoleResponse> getAll(int page, int size, String search) {
        int currentPage = Math.max(page, 1); // 1-based
        Pageable pageable = PageRequest.of(currentPage - 1, size);

        Page<Role> pageData;
        String kw = search == null ? "" : search.trim();
        if (!kw.isBlank()) {
            pageData = roleRepository
                    .findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(kw, kw, pageable);
        } else {
            pageData = roleRepository.findAll(pageable);
        }

        var data = pageData.getContent().stream()
                .map(roleMapper::toRoleResponse)
                .toList();

        return PagedResponse.<RoleResponse>builder()
                .data(data)
                .page(currentPage)
                .size(size)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

    @PreAuthorize("hasRole('delete_role')")
    public void delete(String role) {
        roleRepository.deleteById(role);
    }
}
