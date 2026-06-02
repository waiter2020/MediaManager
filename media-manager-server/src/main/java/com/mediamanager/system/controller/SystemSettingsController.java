package com.mediamanager.system.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.system.dto.AppearanceSettingsDto;
import com.mediamanager.system.dto.AppearanceSettingsUpdateRequest;
import com.mediamanager.system.dto.GeneralSettingsDto;
import com.mediamanager.system.dto.IntegrationsSettingsDto;
import com.mediamanager.system.dto.IntegrationsSettingsUpdateRequest;
import com.mediamanager.system.dto.MediaProcessingSettingsDto;
import com.mediamanager.system.dto.MediaProcessingSettingsUpdateRequest;
import com.mediamanager.system.dto.SecuritySettingsDto;
import com.mediamanager.system.dto.SecuritySettingsUpdateRequest;
import com.mediamanager.system.repository.SysUserRepository;
import com.mediamanager.system.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings", description = "Typed system configuration by domain")
public class SystemSettingsController {

    private final SysConfigService sysConfigService;
    private final SysUserRepository userRepository;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Settings overview (version, setup)")
    public ApiResponse<GeneralSettingsDto> getSummary() {
        boolean setupCompleted = userRepository.count() > 0;
        return ApiResponse.success(sysConfigService.getGeneralSettings(setupCompleted));
    }

    @GetMapping("/security")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Security settings")
    public ApiResponse<SecuritySettingsDto> getSecurity() {
        return ApiResponse.success(sysConfigService.getSecuritySettings());
    }

    @PutMapping("/security")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Update security settings")
    public ApiResponse<SecuritySettingsDto> updateSecurity(@Valid @RequestBody SecuritySettingsUpdateRequest request) {
        return ApiResponse.success(sysConfigService.updateSecuritySettings(request));
    }

    @GetMapping("/media-processing")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "FFmpeg / FFprobe paths")
    public ApiResponse<MediaProcessingSettingsDto> getMediaProcessing() {
        return ApiResponse.success(sysConfigService.getMediaProcessingSettings());
    }

    @PutMapping("/media-processing")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Update media processing paths")
    public ApiResponse<MediaProcessingSettingsDto> updateMediaProcessing(
            @Valid @RequestBody MediaProcessingSettingsUpdateRequest request) {
        return ApiResponse.success(sysConfigService.updateMediaProcessingSettings(request));
    }

    @GetMapping("/integrations")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Third-party integrations (TMDb)")
    public ApiResponse<IntegrationsSettingsDto> getIntegrations() {
        return ApiResponse.success(sysConfigService.getIntegrationsSettings());
    }

    @PutMapping("/integrations")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Update integrations")
    public ApiResponse<IntegrationsSettingsDto> updateIntegrations(
            @Valid @RequestBody IntegrationsSettingsUpdateRequest request) {
        return ApiResponse.success(sysConfigService.updateIntegrationsSettings(request));
    }

    @GetMapping("/appearance")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "UI appearance defaults")
    public ApiResponse<AppearanceSettingsDto> getAppearance() {
        return ApiResponse.success(sysConfigService.getAppearanceSettings());
    }

    @PutMapping("/appearance")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Update UI appearance")
    public ApiResponse<AppearanceSettingsDto> updateAppearance(@Valid @RequestBody AppearanceSettingsUpdateRequest request) {
        return ApiResponse.success(sysConfigService.updateAppearanceSettings(request));
    }
}

