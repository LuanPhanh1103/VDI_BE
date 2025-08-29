package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.PermissionRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.PermissionResponse;
import com.mobifone.vdi.service.PermissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PermissionController {
    PermissionService permissionService;

    @LogApi
    @PostMapping
    ApiResponse<PermissionResponse> create(@RequestBody PermissionRequest request) {
        return ApiResponse.<PermissionResponse>builder()
                .result(permissionService.create(request))
                .build();
    }

//    @GetMapping
//    ApiResponse<PagedResponse<PermissionResponse>> getAll(
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "10") int limit
//    ) {
//        return ApiResponse.<PagedResponse<PermissionResponse>>builder()
//                .result(permissionService.getAll(page, limit))
//                .build();
//    }

    @GetMapping
    public ApiResponse<PagedResponse<PermissionResponse>> getAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "") String search
    ) {
        return ApiResponse.<PagedResponse<PermissionResponse>>builder()
                .result(permissionService.getAll(page, limit, search))
                .build();
    }

    @LogApi
    @DeleteMapping("/{permission}")
    ApiResponse<Void> delete(@PathVariable String permission) {
        permissionService.delete(permission);
        return ApiResponse.<Void>builder().build();
    }
}
