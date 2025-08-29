package com.mobifone.vdi.service;

import com.mobifone.vdi.configuration.RabbitMQConfig;
import com.mobifone.vdi.dto.request.AnsibleJobMessageRequest;
import com.mobifone.vdi.entity.AnsibleJob;
import com.mobifone.vdi.repository.AnsibleJobRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnsibleJobConsumer {
    AnsibleJobRepository jobRepository;
    AnsibleRunnerService ansibleRunnerService;

    @RabbitListener(queues = RabbitMQConfig.INSTALL_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void handleInstall(AnsibleJobMessageRequest jobRequest) {
        log.info("📥 Received INSTALL job: {}", jobRequest);
        saveAndRun(jobRequest);
    }

    @RabbitListener(queues = RabbitMQConfig.CONFIG_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void handleConfig(AnsibleJobMessageRequest jobRequest) {
        log.info("📥 Received CONFIG job: {}", jobRequest);
        saveAndRun(jobRequest);
    }

    private void saveAndRun(AnsibleJobMessageRequest jobRequest) {
        AnsibleJob job = AnsibleJob.builder()
                .jobId(jobRequest.getJobId())
                .targetIps(String.join(",", jobRequest.getTargetIps()))
                .apps(String.join(",", jobRequest.getApps()))
                .winVersion(jobRequest.getWinVersion())
                .linuxVersion(jobRequest.getLinuxVersion())
                .actionType(jobRequest.getActionType())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        jobRepository.save(job);
        ansibleRunnerService.runAnsibleJob(jobRequest);
    }
}
