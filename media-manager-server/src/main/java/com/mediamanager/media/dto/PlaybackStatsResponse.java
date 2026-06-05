package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PlaybackStatsResponse {
    private Long playedItemCount;
    private Long completedItemCount;
    private Long totalPlaybackSeconds;
    private Long favoriteCount;
    private Long watchlistCount;
    private List<MediaItemResponse> recentPlayed;
    private List<MediaItemResponse> mostPlayed;
    private List<MediaItemResponse> watchlist;
}
