package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.InstanceRequest;
import com.mobifone.vdi.dto.request.NoVNCRequest;
import com.mobifone.vdi.dto.response.*;
import com.mobifone.vdi.service.OpenStackService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/openstack")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OpenStackController {

    OpenStackService openStackService;

    @GetMapping("/images")
    ApiResponse<List<ImagesResponse>> images(
            @RequestParam(name="region", defaultValue="default_cluster") String region) {
        return ApiResponse.<List<ImagesResponse>>builder()
                .result(openStackService.getImagesAsPermissions(region))
                .build();
    }

    @GetMapping("/flavors")
    public ApiResponse<PagedResponse<FlavorsResponse>> flavors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(name="region", defaultValue="default_cluster") String region) {
        return ApiResponse.<PagedResponse<FlavorsResponse>>builder()
                .result(openStackService.getFlavorsAsPermissions(page, limit, region))
                .build();
    }

    // PERSONAL
    @LogApi
    @PostMapping("/instance/personal")
    public ApiResponse<InstanceResponse> provisionPersonal(
            @RequestParam(name="user_id") String userId,
            @RequestParam(name="region", defaultValue="default_cluster") String region,
            @RequestBody InstanceRequest request) {
        return ApiResponse.<InstanceResponse>builder()
                .result(openStackService.provisionPersonal(request, userId, region))
                .build();
    }

    // ORGANIZATION
    @LogApi
    @PostMapping("/instance/organization")
    public ApiResponse<InstanceResponse> provisionOrganization(
            @RequestParam(name="user_id") String userId,
            @RequestParam(name="region", defaultValue="default_cluster") String region,
            @RequestBody InstanceRequest request) {
        return ApiResponse.<InstanceResponse>builder()
                .result(openStackService.provisionOrganization(request, userId, region))
                .build();
    }

    // ADD_RESOURCE
    @LogApi
    @PostMapping("/instance/add-resource")
    public ApiResponse<InstanceResponse> addResource(
            @RequestParam(name="user_id") String userId,
            @RequestParam(name="region", defaultValue="default_cluster") String region,
            @RequestBody InstanceRequest request) {
        return ApiResponse.<InstanceResponse>builder()
                .result(openStackService.addResource(request, userId, region))
                .build();
    }

    // DELETE
    @DeleteMapping("/{idInstance}")
    public ApiResponse<InstanceResponse> deleteInstance(
            @PathVariable String idInstance,
            @RequestParam("user_id") String userId,
            @RequestParam(name="region", defaultValue="default_cluster") String region) {
        InstanceResponse res = openStackService.requestDeleteInstance(idInstance, userId, region);
        return ApiResponse.<InstanceResponse>builder().result(res).build();
    }

    // noVNC
    @LogApi
    @PostMapping("/noVNC")
    ApiResponse<NoVNCResponse> noVNC(
            @RequestParam(name="region", defaultValue="default_cluster") String region,
            @RequestBody NoVNCRequest request) {
        return ApiResponse.<NoVNCResponse>builder()
                .result(openStackService.getConsoleUrl(request, region))
                .build();
    }

    // Volumes
    @LogApi
    @GetMapping("/volumes")
    public ApiResponse<PagedResponse<VolumeSummary>> listVolumes(
            @RequestParam("project_id") String projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(name="region", defaultValue="default_cluster") String region) {
        return ApiResponse.<PagedResponse<VolumeSummary>>builder()
                .result(openStackService.getVolumesAsPermissions(projectId, page, limit, region))
                .build();
    }
}
