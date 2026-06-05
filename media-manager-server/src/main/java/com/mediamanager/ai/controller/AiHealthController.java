package com.mediamanager.ai.controller;

import com.mediamanager.ai.service.AiHealthCheckScheduler;
import com.mediamanager.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiHealthController {

    private final AiHealthCheckScheduler healthCheckScheduler;

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<Map<String, Object>> health(
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
        return ApiResponse.success(refresh
                ? healthCheckScheduler.refreshHealthCacheNow()
                : healthCheckScheduler.getCachedHealth());
    }
}
