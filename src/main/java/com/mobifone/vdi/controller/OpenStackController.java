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
    ApiResponse<List<ImagesResponse>> images() {
        return ApiResponse.<List<ImagesResponse>>builder()
                .result(openStackService.getImagesAsPermissions())
                .build();
    }


    @GetMapping("/flavors")
    public ApiResponse<PagedResponse<FlavorsResponse>> flavors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.<PagedResponse<FlavorsResponse>>builder()
                .result(openStackService.getFlavorsAsPermissions(page, limit))
                .build();
    }

    // ========================= Provision APIs =========================
    // PERSONAL: luôn tạo 1 máy
    @LogApi
    @PostMapping("/instance/personal")
    public ApiResponse<InstanceResponse> provisionPersonal(
            @RequestParam(name = "user_id") String userId,
            @RequestBody InstanceRequest request
    ) {
        return ApiResponse.<InstanceResponse>builder()
                .result(openStackService.provisionPersonal(request, userId))
                .build();
    }

    // ORGANIZATION: giới hạn 1 máy
    @LogApi
    @PostMapping("/instance/organization")
    public ApiResponse<InstanceResponse> provisionOrganization(
            @RequestParam(name = "user_id") String userId,
            @RequestBody InstanceRequest request
    ) {
        return ApiResponse.<InstanceResponse>builder()
                .result(openStackService.provisionOrganization(request, userId))
                .build();
    }

    // ADD_RESOURCE: có thể tạo nhiều máy
    @LogApi
    @PostMapping("/instance/add-resource")
    public ApiResponse<InstanceResponse> addResource(
            @RequestParam(name = "user_id") String userId,
            @RequestBody InstanceRequest request
    ) {
        return ApiResponse.<InstanceResponse>builder()
                .result(openStackService.addResource(request, userId))
                .build();
    }

//    @LogApi
//    @PostMapping("/instance")
//    ApiResponse<InstanceResponse> instance(@RequestBody InstanceRequest request) {
//        return ApiResponse.<InstanceResponse>builder()
//                .result(openStackService.provisionPersonal(request))
//                .build();
//    }

    @LogApi
    @GetMapping("/noVNC")
    ApiResponse<NoVNCResponse> noVNC(@RequestBody NoVNCRequest request) {
        return ApiResponse.<NoVNCResponse>builder()
                .result(openStackService.getConsoleUrl(request))
                .build();
    }
}
