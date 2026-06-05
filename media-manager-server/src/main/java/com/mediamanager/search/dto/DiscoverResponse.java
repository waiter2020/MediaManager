package com.mediamanager.search.dto;

import com.mediamanager.media.dto.MediaItemResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiscoverResponse {
    private List<MediaItemResponse> continueWatching;
    private List<MediaItemResponse> watchlist;
    private List<MediaItemResponse> recommended;
    private List<MediaItemResponse> favorites;
    private List<MediaItemResponse> topRated;
    private List<MediaItemResponse> unwatched;
    private List<MediaItemResponse> recentlyAdded;
}
