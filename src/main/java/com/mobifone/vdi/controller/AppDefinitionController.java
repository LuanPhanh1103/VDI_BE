// com.mobifone.vdi.controller.AppDefinitionController.java
package com.mobifone.vdi.controller;

import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.AppDefinitionRequest;
import com.mobifone.vdi.dto.response.AppDefinitionResponse;

import com.mobifone.vdi.dto.response.PagedResponse;

import com.mobifone.vdi.entity.enumeration.ActionType;
import com.mobifone.vdi.service.AppDefinitionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/apps")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AppDefinitionController {

    AppDefinitionService service;

    @PostMapping
    public ApiResponse<AppDefinitionResponse> create(@RequestBody AppDefinitionRequest request) {
        return ApiResponse.<AppDefinitionResponse>builder()
                .result(service.create(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<AppDefinitionResponse> update(@PathVariable String id,
                                                     @RequestBody AppDefinitionRequest request) {
        return ApiResponse.<AppDefinitionResponse>builder()
                .result(service.update(id, request))
                .build();
    }

    @GetMapping
    public ApiResponse<PagedResponse<AppDefinitionResponse>> getAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return ApiResponse.<PagedResponse<AppDefinitionResponse>>builder()
                .result(service.getAll(page, limit, search, actionType, enabled))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<AppDefinitionResponse> get(@PathVariable String id) {
        return ApiResponse.<AppDefinitionResponse>builder()
                .result(service.get(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.<Void>builder().build();
    }
}
