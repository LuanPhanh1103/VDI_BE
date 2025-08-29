package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.configuration.RabbitMQConfig;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.AnsibleJobMessageRequest;
import com.mobifone.vdi.entity.AnsibleJob;
import com.mobifone.vdi.service.AnsibleRunnerService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AnsibleJobController {
    RabbitTemplate rabbitTemplate;
    AnsibleRunnerService ansibleRunnerService;

    @LogApi
    @PostMapping
    public ApiResponse<String> createJob(@RequestBody AnsibleJobMessageRequest job) {
        job.setJobId(UUID.randomUUID().toString());
        String routingKey = job.getActionType().equalsIgnoreCase("install") ?
                RabbitMQConfig.INSTALL_KEY : RabbitMQConfig.CONFIG_KEY;

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, job);

        return ApiResponse.<String>builder()
                .result(job.getJobId()).build();
    }

    @LogApi
    @GetMapping("/detail/{jobId}")
    public ApiResponse<AnsibleJob> getJobStatus(@PathVariable String jobId) {
        return ApiResponse.<AnsibleJob>builder()
                .result(ansibleRunnerService.getJob(jobId)).build();
    }

    @LogApi
    @GetMapping("/detail/{jobId}/log")
    public ApiResponse<String> getJobLog(@PathVariable String jobId) {
        return ApiResponse.<String>builder()
                .result(ansibleRunnerService.getJobLog(jobId)).build();
    }

    @LogApi
    @PostMapping("/{jobId}/cancel")
    public ApiResponse<String> cancel(@PathVariable String jobId) {
        ansibleRunnerService.cancelJob(jobId);
        return ApiResponse.<String>builder()
                .result("Cancel requested for job: " + jobId).build();
    }
}
