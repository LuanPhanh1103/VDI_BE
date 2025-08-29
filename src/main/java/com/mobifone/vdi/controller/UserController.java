package com.mobifone.vdi.controller;

import com.mobifone.vdi.common.LogApi;
import com.mobifone.vdi.dto.ApiResponse;
import com.mobifone.vdi.dto.request.UpdatePasswordRequest;
import com.mobifone.vdi.dto.request.UserCreationRequest;
import com.mobifone.vdi.dto.request.UserUpdateRequest;
import com.mobifone.vdi.dto.response.PagedResponse;
import com.mobifone.vdi.dto.response.UserResponse;
import com.mobifone.vdi.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserController {
    UserService userService;

    @LogApi
    @PostMapping
    ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .build();
    }

    @LogApi
    @GetMapping
    public ApiResponse<PagedResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "") String search
    ) {

        return ApiResponse.<PagedResponse<UserResponse>>builder()
                .result(userService.getUsers(page, limit, search))
                .build();
    }


    @LogApi
    @GetMapping("/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable("userId") String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUser(userId))
                .build();
    }

    @LogApi
    @GetMapping("/my-info")
    ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfo())
                .build();
    }

    @LogApi
    @DeleteMapping("/{userId}")
    ApiResponse<String> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<String>builder().result("User has been deleted").build();
    }

    @LogApi
    @PostMapping("/{userId}")
    ApiResponse<String> updatePasswordUser(
            @PathVariable String userId,
            @RequestBody UpdatePasswordRequest request
    ) {
        userService.updatePassword(request,userId);
        return ApiResponse.<String>builder().result("Password is updated").build();
    }

    @LogApi
    @PutMapping("/{userId}")
    ApiResponse<UserResponse> updateUser(
            @PathVariable String userId,
            @RequestBody UserUpdateRequest request
    ) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUser(userId, request))
                .build();
    }
}
