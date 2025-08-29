package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.ProvisionTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProvisionTaskRepository extends JpaRepository<ProvisionTask, String> {
    Optional<ProvisionTask> findByTaskId(String taskId);
}
