package com.mediamanager.system.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.system.dto.*;
import com.mediamanager.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User Management")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Get all users")
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.success(userService.getAllUsers());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Create a new user")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Get user by ID")
    public ApiResponse<UserResponse> getUserById(@PathVariable Integer id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Update user")
    public ApiResponse<UserResponse> updateUser(@PathVariable Integer id,
                                                @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Delete user")
    public ApiResponse<Void> deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Assign roles to user")
    public ApiResponse<UserResponse> assignRoles(@PathVariable Integer id,
                                                 @RequestBody RoleAssignRequest request) {
        return ApiResponse.success(userService.assignRoles(id, request));
    }

    @GetMapping("/{id}/library-access")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Get library access for user")
    public ApiResponse<List<LibraryAccessRequest.LibraryAccessItem>> getLibraryAccess(@PathVariable Integer id) {
        return ApiResponse.success(userService.getLibraryAccess(id));
    }

    @PutMapping("/{id}/library-access")
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Set library access for user")
    public ApiResponse<Void> setLibraryAccess(@PathVariable Integer id,
                                              @RequestBody LibraryAccessRequest request) {
        userService.setLibraryAccess(id, request);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info")
    public ApiResponse<UserResponse> getCurrentUser() {
        String username = getCurrentUsername();
        return ApiResponse.success(userService.getUserByUsername(username));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ApiResponse<UserResponse> updateCurrentUser(@RequestBody UserUpdateRequest request) {
        String username = getCurrentUsername();
        UserResponse current = userService.getUserByUsername(username);
        return ApiResponse.success(userService.updateUser(current.getId(), request));
    }

    @PutMapping("/me/password")
    @Operation(summary = "Change current user password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = getCurrentUsername();
        UserResponse current = userService.getUserByUsername(username);
        userService.changePassword(current.getId(), request);
        return ApiResponse.success();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
