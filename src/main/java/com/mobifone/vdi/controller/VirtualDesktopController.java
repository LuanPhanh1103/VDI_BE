package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.VirtualDesktopRequest;
import com.mobifone.vdi.dto.request.VirtualDesktopUpdateRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.VirtualDesktopResponse;
import com.mobifone.vdi.service.VirtualDesktopService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/virtualDesktops")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VirtualDesktopController {
    VirtualDesktopService virtualDesktopService;

    @GetMapping
    public ApiResponse<PagedResponse<VirtualDesktopResponse>> resolve(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.<PagedResponse<VirtualDesktopResponse>>builder()
                .result(virtualDesktopService.getVDIsForCurrentUser(projectId, search, page, limit))
                .build();
    }


    @LogApi
    @PostMapping
    ApiResponse<VirtualDesktopResponse> createVirtualDesktop(@RequestBody @Valid VirtualDesktopRequest request) {
        return ApiResponse.<VirtualDesktopResponse>builder()
                .result(virtualDesktopService.createVirtualDesktop(request))
                .build();
    }

//    @GetMapping
//    @LogApi
//    public ApiResponse<PagedResponse<VirtualDesktopResponse>> getAllVirtualDesktops(
//            @RequestParam(defaultValue = "1") int page,
//            @RequestParam(defaultValue = "10") int limit
//    ) {
//        return ApiResponse.<PagedResponse<VirtualDesktopResponse>>builder()
//                .result(virtualDesktopService.getAllVirtualDesktops(page, limit))
//                .build();
//    }


//    @LogApi
//    @GetMapping("/user/{userId}")
//    ApiResponse<List<VirtualDesktopResponse>> getVirtualDesktop(@PathVariable("userId") String userId) {
//        return ApiResponse.<List<VirtualDesktopResponse>>builder()
//                .result(virtualDesktopService.getAllVirtualDesktopsByUserId(userId))
//                .build();
//    }

    @LogApi
    @GetMapping("/{virtualDesktopId}")
    ApiResponse<VirtualDesktopResponse> getVirtualDesktopInfo(@PathVariable String virtualDesktopId) {
        return ApiResponse.<VirtualDesktopResponse>builder()
                .result(virtualDesktopService.getVirtualDesktopInfo(virtualDesktopId))
                .build();
    }

    @LogApi
    @DeleteMapping("/{virtualDesktopId}")
    ApiResponse<String> deleteVirtualDesktop(@PathVariable String virtualDesktopId) {
        virtualDesktopService.deleteVirtualDesktop(virtualDesktopId);
        return ApiResponse.<String>builder().result("VirtualDesktop has been deleted").build();
    }

    @LogApi
    @PutMapping("/{virtualDesktopId}")
    ApiResponse<VirtualDesktopResponse> updateVirtualDesktop(@PathVariable String virtualDesktopId, @RequestBody VirtualDesktopUpdateRequest request) {
        return ApiResponse.<VirtualDesktopResponse>builder()
                .result(virtualDesktopService.updateVirtualDesktop(virtualDesktopId, request))
                .build();
    }
}
