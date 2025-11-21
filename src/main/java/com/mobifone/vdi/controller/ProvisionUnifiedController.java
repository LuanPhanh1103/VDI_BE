package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.ProvisionAndConfigureRequest;
import com.mobifone.vdi.dto.response.JobStatusResponse;
import com.mobifone.vdi.service.ProvisionOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/provision")
@RequiredArgsConstructor
public class ProvisionUnifiedController {

    private final ProvisionOrchestratorService orchestrator;

    /** Trả jobId ngay; phần còn lại chạy nền */
    @LogApi
    @PostMapping("/{mode}")
    public ApiResponse<Map<String, String>> provision(
            @PathVariable String mode,
            @RequestParam(name="region", defaultValue="default_cluster") String region,  // <== thêm
            @Valid @RequestBody ProvisionAndConfigureRequest req) {

        String jobId = orchestrator.submit(mode, req, region);  // <== truyền region
        return ApiResponse.<Map<String, String>>builder()
                .result(Map.of("jobId", jobId))
                .message("Accepted")
                .build();
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<JobStatusResponse> view(@PathVariable String jobId) {
        return ApiResponse.<JobStatusResponse>builder()
                .result(orchestrator.getStatus(jobId))
                .build();
    }
}
