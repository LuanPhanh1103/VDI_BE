package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.ApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiLogRepository extends JpaRepository<ApiLog, String> {
}
