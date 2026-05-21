package com.mediamanager.search.dto;

import com.mediamanager.media.dto.MediaItemResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DiscoverResponse {
    private List<MediaItemResponse> continueWatching;
    private List<MediaItemResponse> recommended;
    private List<MediaItemResponse> recentlyAdded;
}
