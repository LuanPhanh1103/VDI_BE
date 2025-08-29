package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.AnsibleJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnsibleJobRepository extends JpaRepository<AnsibleJob, String> {
   Optional<AnsibleJob> findByJobId(String jobId);
}
