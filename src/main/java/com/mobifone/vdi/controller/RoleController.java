package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.RoleRequest;
import com.mobifone.vdi.dto.request.RoleUpdateRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.RoleResponse;
import com.mobifone.vdi.service.RoleService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RoleController {
    RoleService roleService;

    @LogApi
    @PostMapping
    ApiResponse<RoleResponse> create(@RequestBody RoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.create(request))
                .build();
    }

//    @GetMapping
//    @LogApi
//    public ApiResponse<PagedResponse<RoleResponse>> getAllRoles(
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "10") int limit
//    ) {
//        return ApiResponse.<PagedResponse<RoleResponse>>builder()
//                .result(roleService.getAll(page, limit))
//                .build();
//    }


    @GetMapping
    public ApiResponse<PagedResponse<RoleResponse>> getAllRoles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "") String search
    ) {
        return ApiResponse.<PagedResponse<RoleResponse>>builder()
                .result(roleService.getAll(page, limit, search))
                .build();
    }


    @LogApi
    @DeleteMapping("/{role}")
    ApiResponse<Void> delete(@PathVariable String role) {
        roleService.delete(role);
        return ApiResponse.<Void>builder().build();
    }

    @LogApi
    @PutMapping("/{roleName}")
    ApiResponse<RoleResponse> updateUser(@PathVariable String roleName, @RequestBody RoleUpdateRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.updateRole(roleName, request))
                .build();
    }
}
