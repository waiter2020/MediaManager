package com.mediamanager.ai.controller;

import com.mediamanager.ai.dto.AiConfigDto;
import com.mediamanager.ai.dto.AiConfigUpdateRequest;
import com.mediamanager.ai.service.AiConfigService;
import com.mediamanager.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Config", description = "Configurable AI providers")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "List registered AI providers")
    public ApiResponse<List<Map<String, Object>>> listProviders() {
        return ApiResponse.success(aiConfigService.listProviders());
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Get global AI configuration")
    public ApiResponse<AiConfigDto> getConfig() {
        return ApiResponse.success(aiConfigService.getConfig());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Update global AI configuration")
    public ApiResponse<AiConfigDto> updateConfig(@Valid @RequestBody AiConfigUpdateRequest request) {
        return ApiResponse.success(aiConfigService.updateConfig(request));
    }
}

