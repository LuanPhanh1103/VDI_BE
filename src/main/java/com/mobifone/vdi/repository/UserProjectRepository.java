package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.UserProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserProjectRepository extends JpaRepository<UserProject, String> {
    boolean existsByUser_IdAndProject_Id(String userId, String projectId);
    List<UserProject> findAllByProject_Id(String projectId);
    List<UserProject> findAllByUser_Id(String userId);
}
