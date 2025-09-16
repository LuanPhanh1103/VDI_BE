package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.JobStepLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobStepLogRepository extends JpaRepository<JobStepLog, String> {
    List<JobStepLog> findByJobIdOrderByCreatedAtAsc(String jobId);
}
