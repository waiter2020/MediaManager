package com.mediamanager.ai.controller;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiHealthController {

    private final AiOrchestrator aiOrchestrator;

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<Map<String, Object>> health() {
        AiProvider provider = aiOrchestrator.resolve(AiTaskType.EMBED_TEXT);
        float[] probe = provider.embedText("health-check", aiOrchestrator.defaultConfig());
        return ApiResponse.success(Map.of(
                "provider", provider.providerId(),
                "embeddingDimensions", probe.length,
                "status", probe.length > 0 ? "ok" : "degraded"));
    }
}
