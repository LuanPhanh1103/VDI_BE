package com.mobifone.vdi.service;

import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.entity.UserProject;
import com.mobifone.vdi.entity.enumeration.ProjectRole;
import com.mobifone.vdi.repository.UserProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

// com.mobifone.vdi.service.UserProjectService
@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProjectService {

    UserProjectRepository userProjectRepo;
    @PersistenceContext
    EntityManager em;

    @Transactional(readOnly = true)
    public boolean isOwnerOfAnyProject(String userId) {
        return userProjectRepo.existsByUser_IdAndProjectRole(userId, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<String> findProjectIdsOwnedBy(String userId) {
        return userProjectRepo.findProjectIdsByUserAndRole(userId, ProjectRole.OWNER);
    }

    @Transactional
    public void addMember(String projectId, User user, ProjectRole role) {
        Project ref = em.getReference(Project.class, projectId);
        if (userProjectRepo.existsByUser_IdAndProject_Id(user.getId(), projectId)) return;
        userProjectRepo.save(UserProject.builder().project(ref).user(user).projectRole(role).build());
    }

    @Transactional
    public void removeMember(String projectId, String userId) {
        var rows = userProjectRepo.findAllByProject_Id(projectId).stream()
                .filter(up -> up.getUser().getId().equals(userId) && up.getProjectRole()!=ProjectRole.OWNER)
                .toList();
        userProjectRepo.deleteAll(rows);
    }

    @Transactional
    public void softDeleteAllByProject(String projectId) {
        var rows = userProjectRepo.findAllByProject_Id(projectId);
        for (var r : rows) r.setIsDeleted(1L);
        userProjectRepo.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public Set<String> findUserIdsByProject(String projectId) {
        return userProjectRepo.findAllByProject_Id(projectId)
                .stream().map(up -> up.getUser().getId()).collect(java.util.stream.Collectors.toSet());
    }
}

