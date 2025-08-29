package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.ProjectMemberRequest;
import com.mobifone.vdi.dto.request.ProjectRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.ProjectResponse;
import com.mobifone.vdi.service.ProjectService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProjectController {
    private final ProjectService projectService;

    @LogApi
    @PostMapping
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectRequest req) {
        return ApiResponse.<ProjectResponse>builder()
                .result(projectService.create(req)).build();
    }

    @LogApi
    @PostMapping("/members")
    public ApiResponse<String> addMember(@Valid @RequestBody ProjectMemberRequest req) {
        // req.projectId phải = id (có thể verify)
        projectService.addMember(req);
        return ApiResponse.<String>builder().result("Thêm thành công").build();
    }

    @LogApi
    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<String> removeMember(@PathVariable String id, @PathVariable String userId) {
        projectService.removeMember(id, userId);
        return ApiResponse.<String>builder().result("Xóa thành công").build();
    }

//    @GetMapping
//    public ApiResponse<PagedResponse<ProjectResponse>> getAll(
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "10") int limit
//    ) {
//        return ApiResponse.<PagedResponse<ProjectResponse>>builder()
//                .result(projectService.getAll(page, limit))
//                .build();
//    }

    @GetMapping
    public ApiResponse<PagedResponse<ProjectResponse>> getAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "") String search
    ) {
        return ApiResponse.<PagedResponse<ProjectResponse>>builder()
                .result(projectService.getAll(page, limit, search))
                .build();
    }


    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> getDetail(@PathVariable String id) {
        return ApiResponse.<ProjectResponse>builder()
                .result(projectService.getById(id))
                .build();
    }


    @LogApi
    @DeleteMapping("/{id}")
    public ApiResponse<String> softDelete(@PathVariable String id) {
        projectService.softDelete(id);
        return ApiResponse.<String>builder().result("Xóa project thành công").build();
    }
}
