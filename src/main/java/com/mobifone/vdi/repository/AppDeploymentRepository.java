package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.AppDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppDeploymentRepository extends JpaRepository<AppDeployment, String> {
    List<AppDeployment> findByJobId(String jobId);
    List<AppDeployment> findByVdIdOrderByStartedAtAsc(String vdId);
    Optional<AppDeployment> findOneByJobIdAndVdIdAndAppCode(String jobId, String vdId, String appCode);
}
