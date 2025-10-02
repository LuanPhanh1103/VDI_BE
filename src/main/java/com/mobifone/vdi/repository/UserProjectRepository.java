package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.UserProject;
import com.mobifone.vdi.entity.enumeration.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserProjectRepository extends JpaRepository<UserProject, String> {
    boolean existsByUser_IdAndProject_Id(String userId, String projectId);
    List<UserProject> findAllByProject_Id(String projectId);
    boolean existsByUser_IdAndProjectRole(String userId, ProjectRole role);

    @Query("select up.project.id from UserProject up where up.user.id = :uid and up.projectRole = :role")
    List<String> findProjectIdsByUserAndRole(@Param("uid") String uid, @Param("role") ProjectRole role);
}
