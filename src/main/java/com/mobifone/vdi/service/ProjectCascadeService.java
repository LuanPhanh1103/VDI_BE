package com.mobifone.vdi.service;

import com.mobifone.vdi.entity.Project;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProjectCascadeService {

    ProjectRepository projectRepo;
    VirtualDesktopService virtualDesktopService;
    UserProjectService userProjectService;
    UserService userService;

    /** Được gọi khi nhận event destroy infra OK */
    @Transactional
    public void cascadeMarkProjectDeleted(String projectId) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_EXISTED));

        //  1) XÓA VDI TRƯỚC
        virtualDesktopService.markAllDeletedByProject(projectId);

        //  2) XÓA user_project TRƯỚC
        userProjectService.softDeleteAllByProject(projectId);

        //  3) RESET PASSWORD USER TRONG PROJECT TRƯỚC
        userService.resetPasswordsForUsersInProject(projectId);

        //  4) CUỐI CÙNG MỚI XÓA PROJECT
        project.setIsDeleted(1L);
        projectRepo.save(project);

        log.info("[Cascade] Marked project {} and related entities deleted", projectId);
    }
}
