package com.mobifone.vdi.service;

import com.mobifone.vdi.constant.PredefinedRole;
import com.mobifone.vdi.dto.request.UpdatePasswordRequest;
import com.mobifone.vdi.dto.request.UserCreationRequest;
import com.mobifone.vdi.dto.request.UserUpdateRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.UserResponse;
import com.mobifone.vdi.entity.Role;
import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.entity.enumeration.ProjectRole;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.mapper.UserMapper;
import com.mobifone.vdi.repository.RoleRepository;
import com.mobifone.vdi.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserService {
    UserRepository userRepository;   // OK (cùng domain)
    RoleRepository roleRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    UserProjectService userProjectService; // NEW

    @PreAuthorize("hasRole('create_user')")
    @Transactional
    public UserResponse createUser(UserCreationRequest request) {
        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        HashSet<Role> roles = new HashSet<>();
        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);
        user.setRoles(roles);

        try { user = userRepository.save(user); }
        catch (DataIntegrityViolationException e) { throw new AppException(ErrorCode.USER_EXISTED); }

        // Nếu current user là OWNER: gán user mới vào tất cả project mình làm chủ
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String ownerId = userRepository.findByUsername(auth.getName())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED))
                    .getId();
            var projectIds = userProjectService.findProjectIdsOwnedBy(ownerId);
            for (String pid : projectIds) {
                userProjectService.addMember(pid, user, ProjectRole.MEMBER);
            }
        }
        return userMapper.toUserResponse(user);
    }


//    @PreAuthorize("hasRole('create_user')")
//    public UserResponse createUser(UserCreationRequest request) {
//        User user = userMapper.toUser(request);
//        user.setPassword(passwordEncoder.encode(request.getPassword()));
//
//        HashSet<Role> roles = new HashSet<>();
//        roleRepository.findById(PredefinedRole.USER_ROLE).ifPresent(roles::add);
//
//        user.setRoles(roles);
//
//        try {
//            user = userRepository.save(user);
//        } catch (DataIntegrityViolationException exception) {
//            throw new AppException(ErrorCode.USER_EXISTED);
//        }
//
//        return userMapper.toUserResponse(user);
//    }

    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userMapper.toUserResponse(user);
    }

    public User findUserByUsername(String username){
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    public User findUserById(String id){
        return userRepository
                .findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    public boolean userExitedById(String id){
        return userRepository.existsById(id);
    }

    @PreAuthorize("hasRole('update_user')")
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        userMapper.updateUser(user, request);

        var roles = roleRepository.findAllById(request.getRoles());
        user.setRoles(new HashSet<>(roles));

        return userMapper.toUserResponse(userRepository.save(user));
    }

    public void updatePassword(UpdatePasswordRequest request, String userId){
        User user = userRepository.findById(userId).orElseThrow(
                () -> new AppException(ErrorCode.USER_NOT_EXISTED)
        );
        boolean comparePassword = passwordEncoder.matches(request.getOldPassword(), user.getPassword());

        if (!comparePassword) throw new AppException(ErrorCode.WRONG_PASSWORD);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @PreAuthorize("hasRole('delete_user')")
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        user.setIsDeleted(1L);
        userRepository.save(user);
    }

//    @PreAuthorize("hasRole('get_users')")
//    public PagedResponse<UserResponse> getUsers(int page, int size, String search) {
//        log.info("In method get Users");
//        int currentPage = Math.max(page, 1);
//        Pageable pageable = PageRequest.of(currentPage - 1, size);
//
//        Page<User> pageData;
//        if (search != null && !search.isBlank()) {
//            String kw = search.trim();
//            pageData = userRepository
//                    .findByFirstNameIgnoreCaseContainingOrLastNameIgnoreCaseContaining(kw, kw, pageable);
//        } else {
//            pageData = userRepository.findAll(pageable);
//        }
//
//        var data = pageData.getContent().stream()
//                .map(userMapper::toUserResponse)
//                .toList();
//
//        return PagedResponse.<UserResponse>builder()
//                .data(data)
//                .page(currentPage)
//                .size(size)
//                .totalElements(pageData.getTotalElements())
//                .totalPages(pageData.getTotalPages())
//                .build();
//    }

    @PreAuthorize("hasRole('get_users')")
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(int page, int size, String search) {
        int currentPage = Math.max(page, 1);
        Pageable pageable = PageRequest.of(currentPage - 1, Math.max(size,1));
        String kw = (search == null) ? "" : search.trim();

        Page<User> pageData;
        if (isAdmin()) {
            pageData = kw.isBlank()
                    ? userRepository.findAllWithRoles(pageable)
                    : userRepository.searchAllUsers(kw, pageable);
        } else {
            String uid = getMyInfo().getId();
            if (!userProjectService.isOwnerOfAnyProject(uid)) {
                throw new AppException(ErrorCode.PERMISSION_DENIED);
            }
            // vẫn dùng các query đã tối ưu sẵn trong UserRepository theo owner scope
            pageData = kw.isBlank()
                    ? userRepository.findUsersInOwnerScope(uid, pageable)
                    : userRepository.searchUsersInOwnerScope(uid, kw, pageable);
        }

        var data = pageData.getContent().stream().map(userMapper::toUserResponse).toList();
        return PagedResponse.<UserResponse>builder()
                .data(data).page(currentPage).size(size)
                .totalElements(pageData.getTotalElements())
                .totalPages(pageData.getTotalPages()).build();
    }

    /** Được ProjectService gọi khi destroy infra OK */
    @Transactional
    public void resetPasswordsForUsersInProject(String projectId) {
        var ids = userProjectService.findUserIdsByProject(projectId);
        if (ids.isEmpty()) return;
        var users = userRepository.findAllById(ids);
        for (var u : users) u.setPassword("1"); // theo yêu cầu
        userRepository.saveAll(users);
    }

    // Helper giống các service trước
    private boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> {
            var au = a.getAuthority();
            return "admin".equalsIgnoreCase(au) || "ROLE_admin".equalsIgnoreCase(au);
        });
    }



    // userService.findUserByUsername(...)
    public User findUserByUsernameROLE(String username) {
        return userRepository.findByUsernameWithRolesAndPermissions(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }


    @PreAuthorize("hasRole('get_user')")
    public UserResponse getUser(String id) {
        return userMapper.toUserResponse(
                userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED)));
    }
}
