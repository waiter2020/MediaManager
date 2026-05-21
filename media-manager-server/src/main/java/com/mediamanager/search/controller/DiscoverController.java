package com.mediamanager.search.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.search.dto.DiscoverResponse;
import com.mediamanager.search.service.DiscoverService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discover")
@RequiredArgsConstructor
@Tag(name = "Discover", description = "Home recommendations")
public class DiscoverController {

    private final DiscoverService discoverService;

    @GetMapping
    @PreAuthorize("hasAuthority('media:view')")
    public ApiResponse<DiscoverResponse> discover(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(discoverService.discover(limit));
    }
}
