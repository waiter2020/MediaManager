package com.mediamanager.system.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.system.dto.LoginRequest;
import com.mediamanager.system.dto.LoginResponse;
import com.mediamanager.system.dto.RefreshTokenRequest;
import com.mediamanager.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Auth APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "User login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout")
    public ApiResponse<Void> logout(@RequestParam String refreshToken) {
        authService.logout(refreshToken);
        return ApiResponse.success();
    }

    @PostMapping("/setup")
    @Operation(summary = "Initial setup to create super admin")
    public ApiResponse<Void> setup(@Valid @RequestBody LoginRequest request) {
        authService.setupFirstUser(request);
        return ApiResponse.success();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token with refresh token")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request.getRefreshToken()));
    }
}
