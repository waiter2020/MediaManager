package com.mediamanager.media.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.dto.PlaybackStatsResponse;
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

    @GetMapping("/continue-watching")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get unfinished playback progress for current user")
    public ApiResponse<List<MediaItemResponse>> getContinueWatching(
            @AuthenticationPrincipal SysUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getContinueWatching(user.getId(), Math.min(limit, 50)));
    }

    @GetMapping("/recent/favorites")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get recently favorited media for current user")
    public ApiResponse<List<MediaItemResponse>> getRecentFavorites(
            @AuthenticationPrincipal SysUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getRecentFavorites(user.getId(), Math.min(limit, 50)));
    }

    @GetMapping("/watchlist")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get current user's watchlist")
    public ApiResponse<List<MediaItemResponse>> getWatchlist(
            @AuthenticationPrincipal SysUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getWatchlist(user.getId(), Math.min(limit, 50)));
    }

    @GetMapping("/playback/stats")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get playback statistics for current user")
    public ApiResponse<PlaybackStatsResponse> getPlaybackStats(
            @AuthenticationPrincipal SysUser user,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getPlaybackStats(user.getId(), Math.min(limit, 50)));
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
        Integer durationSeconds = body.containsKey("durationSeconds") && body.get("durationSeconds") != null
                ? ((Number) body.get("durationSeconds")).intValue() : null;
        Boolean completed = body.containsKey("completed") && body.get("completed") != null
                ? (Boolean) body.get("completed") : null;
        activityService.recordPlayback(user.getId(), mediaItemId, position, durationSeconds, completed);
        return ApiResponse.success();
    }

    @PostMapping("/favorite/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Toggle favorite status for a media item")
    public ApiResponse<Map<String, Boolean>> toggleFavorite(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean favorited = activityService.toggleFavorite(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("favorite", favorited, "favorited", favorited));
    }

    @GetMapping("/favorite/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Check if media item is favorited by current user")
    public ApiResponse<Map<String, Boolean>> isFavorited(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean favorited = activityService.isFavorited(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("favorite", favorited, "favorited", favorited));
    }

    @PostMapping("/watchlist/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Toggle watchlist status for a media item")
    public ApiResponse<Map<String, Boolean>> toggleWatchlist(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean watchlisted = activityService.toggleWatchlist(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("watchlist", watchlisted, "watchlisted", watchlisted));
    }

    @GetMapping("/watchlist/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Check if media item is in current user's watchlist")
    public ApiResponse<Map<String, Boolean>> isWatchlisted(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean watchlisted = activityService.isWatchlisted(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("watchlist", watchlisted, "watchlisted", watchlisted));
    }

    @PostMapping("/watched/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Set watched status for a media item")
    public ApiResponse<Map<String, Boolean>> setWatched(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId,
            @RequestBody Map<String, Object> body) {
        boolean watched = body.containsKey("watched") && Boolean.TRUE.equals(body.get("watched"));
        boolean result = activityService.setWatched(user.getId(), mediaItemId, watched);
        return ApiResponse.success(Map.of("watched", result));
    }

    @GetMapping("/watched/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Check watched status for a media item")
    public ApiResponse<Map<String, Boolean>> isWatched(
            @AuthenticationPrincipal SysUser user,
            @PathVariable Integer mediaItemId) {
        boolean watched = activityService.isWatched(user.getId(), mediaItemId);
        return ApiResponse.success(Map.of("watched", watched));
    }
}
