package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.InstanceRequest;
import com.mobifone.vdi.dto.request.NoVNCRequest;
import com.mobifone.vdi.dto.response.FlavorsResponse;
import com.mobifone.vdi.dto.response.ImagesResponse;
import com.mobifone.vdi.dto.response.InstanceResponse;
import com.mobifone.vdi.dto.response.NoVNCResponse;
import com.mobifone.vdi.service.OpenStackService;
import com.mobifone.vdi.service.ProvisionPersistService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/check-infra-task")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProvisionController {
    ProvisionPersistService persistService;

//    @LogApi
//    @GetMapping("/{taskId}")
//    public ResponseEntity<?> get(@PathVariable("taskId") String taskId) {
//        return persistService.view(taskId)
//                .map(ResponseEntity::ok)
//                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "task_id not found")));
//    }
    @LogApi
    @GetMapping("/{taskId}")
    public ApiResponse<?> get(@PathVariable("taskId") String taskId) {
        return persistService.view(taskId)
                .map(result -> ApiResponse.builder()
                        .result(result)
                        .message("Success")
                        .build())
                .orElseGet(() -> ApiResponse.builder()
                        .code(404)
                        .message("task_id not found")
                        .build());
    }

}
