package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.AppDeployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppDeploymentRepository extends JpaRepository<AppDeployment, String> {
    List<AppDeployment> findByVdIdOrderByStartedAtAsc(String vdId);
    List<AppDeployment> findByJobId(String jobId);
}
