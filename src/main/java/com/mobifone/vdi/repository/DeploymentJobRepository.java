package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.DeploymentJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, String> {

}
