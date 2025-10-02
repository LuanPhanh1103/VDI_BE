// com.mobifone.vdi.service/AppDefinitionService.java
package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.AppDefinitionRequest;
import com.mobifone.vdi.dto.response.AppDefinitionResponse;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.entity.AppDefinition;

import com.mobifone.vdi.entity.enumeration.ActionType;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.mapper.AppDefinitionMapper;
import com.mobifone.vdi.repository.AppDefinitionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AppDefinitionService {

    AppDefinitionRepository repo;
    AppDefinitionMapper mapper;

    @PreAuthorize("hasRole('create_app')")
    public AppDefinitionResponse create(AppDefinitionRequest request) {
        if (repo.existsByCode(request.getCode())) {
            throw new AppException(ErrorCode.APP_DEPLOYMENT_EXISTED); // hoặc tạo ErrorCode riêng: APP_EXISTS
        }
        AppDefinition entity = mapper.toEntity(request);
        entity = repo.save(entity);
        return mapper.toResponse(entity);
    }

    @PreAuthorize("hasRole('update_app')")
    public AppDefinitionResponse update(String id, AppDefinitionRequest request) {
        AppDefinition entity = repo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_NOT_FOUND));
        mapper.update(entity, request);
        entity = repo.save(entity);
        return mapper.toResponse(entity);
    }

    @PreAuthorize("hasRole('get_apps')")
    public PagedResponse<AppDefinitionResponse> getAll(int page, int size, String search,
                                                       ActionType actionType, Boolean enabled) {
        int currentPage = Math.max(page, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, size, Sort.by("code").ascending());

        Page<AppDefinition> pageData;

        boolean hasSearch = search != null && !search.trim().isEmpty();
        if (hasSearch) {
            pageData = repo.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(search.trim(), search.trim(), pageable);
        } else if (actionType != null && enabled != null) {
            pageData = repo.findByActionTypeAndEnabled(actionType, enabled, pageable);
        } else if (actionType != null) {
            pageData = repo.findByActionType(actionType, pageable);
        } else if (enabled != null) {
            pageData = repo.findByEnabled(enabled, pageable);
        } else {
            pageData = repo.findAll(pageable);
        }

        List<AppDefinitionResponse> data = pageData.getContent().stream()
                .map(mapper::toResponse)
                .toList();

        return PagedResponse.<AppDefinitionResponse>builder()
                .data(data)
                .page(currentPage)
                .size(size)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages())
                .build();
    }

    @PreAuthorize("hasRole('get_app')")
    public AppDefinitionResponse get(String id) {
        return mapper.toResponse(repo.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_NOT_FOUND)));
    }

    @PreAuthorize("hasRole('delete_app')")
    public void delete(String id) {
        repo.deleteById(id);
    }

    // ==== Dùng nội bộ cho Orchestrator (không gắn PreAuthorize để chạy trong thread nền) ====
    @Transactional(readOnly = true)
    public AppDefinition getEntityByCodeOrThrow(String code) {
        return repo.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.ANSIBLE_NOT_FOUND));
    }
}
