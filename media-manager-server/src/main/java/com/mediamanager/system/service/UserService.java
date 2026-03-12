package com.mediamanager.system.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.system.dto.*;
import com.mediamanager.system.entity.LibraryAccess;
import com.mediamanager.system.entity.SysRole;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.LibraryAccessRepository;
import com.mediamanager.system.repository.SysRoleRepository;
import com.mediamanager.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final LibraryAccessRepository libraryAccessRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public UserResponse getUserById(Integer id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toResponse(user);
    }

    public UserResponse getUserByUsername(String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toResponse(user);
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        SysRole userRole = roleRepository.findByCode("USER")
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "USER role not found"));

        SysUser user = SysUser.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .email(request.getEmail())
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();
        user.getRoles().add(userRole);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(Integer id, UserUpdateRequest request) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Integer id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        userRepository.delete(user);
    }

    @Transactional
    public UserResponse assignRoles(Integer userId, RoleAssignRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Set<SysRole> roles = new HashSet<>();
        for (String code : request.getRoleCodes()) {
            SysRole role = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Role not found: " + code));
            roles.add(role);
        }
        user.setRoles(roles);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void setLibraryAccess(Integer userId, LibraryAccessRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        libraryAccessRepository.deleteByUser_Id(userId);

        if (request.getItems() != null) {
            for (LibraryAccessRequest.LibraryAccessItem item : request.getItems()) {
                LibraryAccess access = LibraryAccess.builder()
                        .user(user)
                        .libraryId(item.getLibraryId())
                        .canView(item.getCanView())
                        .canEdit(item.getCanEdit())
                        .canDeleteFile(item.getCanDeleteFile())
                        .build();
                libraryAccessRepository.save(access);
            }
        }
    }

    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private UserResponse toResponse(SysUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatarPath(user.getAvatarPath())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .roles(user.getRoles().stream()
                        .map(r -> UserResponse.RoleInfo.builder()
                                .id(r.getId())
                                .code(r.getCode())
                                .name(r.getName())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
