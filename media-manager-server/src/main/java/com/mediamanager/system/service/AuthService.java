package com.mediamanager.system.service;

import com.mediamanager.common.util.PasswordValidator;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.security.JwtTokenProvider;
import com.mediamanager.system.dto.LoginRequest;
import com.mediamanager.system.dto.LoginResponse;
import com.mediamanager.system.dto.SetupStatusResponse;
import com.mediamanager.system.entity.SysRefreshToken;
import com.mediamanager.system.entity.SysRole;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.SysRefreshTokenRepository;
import com.mediamanager.system.repository.SysRoleRepository;
import com.mediamanager.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        Set<String> roles = user.getRoles().stream()
                .map(SysRole::getCode)
                .collect(Collectors.toSet());

        Set<String> permissions = UserService.collectPermissions(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), permissions);
        String refreshTokenString = tokenProvider.generateRefreshTokenValue();

        SysRefreshToken refreshToken = SysRefreshToken.builder()
                .user(user)
                .token(refreshTokenString)
                .expiresAt(Instant.now().plusMillis(tokenProvider.getRefreshTokenExpiration()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .expiresIn(tokenProvider.getRefreshTokenExpiration() / 1000)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .avatarPath(user.getAvatarPath())
                        .roles(roles)
                        .permissions(permissions)
                        .build())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public LoginResponse refreshToken(String refreshTokenValue) {
        SysRefreshToken refreshToken = refreshTokenRepository.findByTokenAndRevokedFalse(refreshTokenValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        SysUser user = refreshToken.getUser();
        if (user == null || !user.getEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        Set<String> roles = user.getRoles().stream()
                .map(SysRole::getCode)
                .collect(Collectors.toSet());

        Set<String> permissions = UserService.collectPermissions(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), permissions);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(tokenProvider.getRefreshTokenExpiration() / 1000)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .avatarPath(user.getAvatarPath())
                        .roles(roles)
                        .permissions(permissions)
                        .build())
                .build();
    }

    @Transactional
    public void setupFirstUser(LoginRequest request) {
        if (userRepository.count() > 0) {
            throw new BusinessException(ErrorCode.SETUP_ALREADY_COMPLETED);
        }

        SysRole superAdminRole = roleRepository.findByCode("SUPER_ADMIN")
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "SUPER_ADMIN role not found"));

        SysUser superAdmin = SysUser.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName("Super Admin")
                .enabled(true)
                .build();
        superAdmin.getRoles().add(superAdminRole);

        userRepository.save(superAdmin);
    }

    @Transactional(readOnly = true)
    public SetupStatusResponse getSetupStatus() {
        return SetupStatusResponse.builder()
                .setupCompleted(userRepository.count() > 0)
                .build();
    }
}
