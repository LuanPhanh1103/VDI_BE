package com.mobifone.vdi.configuration;

import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.repository.ProjectRepository;
import com.mobifone.vdi.repository.UserProjectRepository;
import com.mobifone.vdi.repository.UserRepository;
import com.mobifone.vdi.repository.VirtualDesktopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("projectSecurity")
@RequiredArgsConstructor
public class ProjectSecurity {
    private final ProjectRepository projectRepo;
    private final UserProjectRepository userProjectRepo;
    private final VirtualDesktopRepository vdiRepo;
    private final UserRepository userRepo; // hoặc dùng UserService

    private String currentUserId(Authentication auth) {
        var username = auth.getName();
        return userRepo.findByUsername(username).map(User::getId).orElse(null);
    }

    public boolean isOwner(String projectId, Authentication auth) {
        var uid = currentUserId(auth);
        return projectRepo.findById(projectId)
                .map(p -> p.getOwner().getId().equals(uid))
                .orElse(false);
    }

    public boolean isMember(String projectId, Authentication auth) {
        var uid = currentUserId(auth);
        return userProjectRepo.existsByUser_IdAndProject_Id(uid, projectId);
    }

    public boolean canReadVDI(String vdiId, Authentication auth) {
        var uid = currentUserId(auth);
        var vdOpt = vdiRepo.findById(vdiId);
        if (vdOpt.isEmpty()) return false;
        var vd = vdOpt.get();
        return vd.getProject().getOwner().getId().equals(uid)
                || (vd.getUser() != null && vd.getUser().getId().equals(uid));
    }

    public boolean canUpdateVDI(String vdiId, Authentication auth) {
        // Chính sách: owner/admin; hoặc assignee tuỳ bạn
        return canReadVDI(vdiId, auth);
    }

    public boolean canDeleteVDI(String vdiId, Authentication auth) {
        var uid = currentUserId(auth);
        var vd = vdiRepo.findById(vdiId).orElse(null);
        return vd != null && vd.getProject().getOwner().getId().equals(uid);
    }
}

