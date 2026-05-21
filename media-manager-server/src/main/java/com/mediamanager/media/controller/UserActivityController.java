package com.mediamanager.media.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.service.UserActivityService;
import com.mediamanager.system.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "User Activity", description = "User playback history and favorites")
public class UserActivityController {

    private final UserActivityService activityService;

    @GetMapping("/recent/played")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get recently played media for current user")
    public ApiResponse<List<MediaItemResponse>> getRecentPlayed(
            @AuthenticationPrincipal SysUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getRecentPlayed(user.getId(), Math.min(limit, 50)));
    }

    @GetMapping("/recent/favorites")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get recently favorited media for current user")
    public ApiResponse<List<MediaItemResponse>> getRecentFavorites(
            @AuthenticationPrincipal SysUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getRecentFavorites(user.getId(), Math.min(limit, 50)));
    }

    @PostMapping("/play")
    @PreAuthorize("hasAuthority('media:play')")
    @Operation(summary = "Record a playback event")
    public ApiResponse<Void> recordPlay(
            @AuthenticationPrincipal SysUser user,
            @RequestBody Map<String, Object> body) {
        Integer mediaItemId = ((Number) body.get("mediaItemId")).intValue();
        Integer position = body.containsKey("position") && body.get("position") != null
                ? ((Number) body.get("position")).intValue() : null;
        activityService.recordPlayback(user.getId(), mediaItemId, position);
        return ApiResponse.success();
    }

    @PostMapping("/favorite/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Toggle favorite status for a media item")
    public ApiResponse<Map<String, Boolean>> toggleFavorite(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean favorited = activityService.toggleFavorite(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("favorited", favorited));
    }

    @GetMapping("/favorite/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Check if media item is favorited by current user")
    public ApiResponse<Map<String, Boolean>> isFavorited(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean favorited = activityService.isFavorited(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("favorited", favorited));
    }
}
