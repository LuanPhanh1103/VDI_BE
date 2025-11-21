package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.DeploymentJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentJobRepository extends JpaRepository<DeploymentJob, String> {

}
