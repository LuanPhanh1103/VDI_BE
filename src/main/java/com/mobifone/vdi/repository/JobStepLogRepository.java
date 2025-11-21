package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.JobStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobStepLogRepository extends JpaRepository<JobStepLog, String> {
    List<JobStepLog> findByJobIdOrderByCreatedAtAsc(String jobId);
}
