package com.mobifone.vdi.service;

import com.mobifone.vdi.entity.AppDeployment;
import com.mobifone.vdi.repository.AppDeploymentRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AppDeploymentService {

    AppDeploymentRepository appRepo;

    @Transactional(readOnly = true)
    public List<AppDeployment> findByJobId(String jobId) {
        return appRepo.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public List<AppDeployment> findByVdIdOrderByStartedAtAsc(String vdId) {
        return appRepo.findByVdIdOrderByStartedAtAsc(vdId);
    }

    @Transactional(readOnly = true)
    public Optional<AppDeployment> findOneByJobVdCode(String jobId, String vdId, String appCode) {
        return appRepo.findOneByJobIdAndVdIdAndAppCode(jobId, vdId, appCode);
    }

    @Transactional
    public AppDeployment save(AppDeployment ad) {
        return appRepo.save(ad);
    }
}

