package com.mediamanager.search.dto;

import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NlSearchResult {
    private PageResult<MediaItemResponse> results;
    /** Parsed filters from NL query (keyword, type, minYear, maxYear, minRating). */
    private Map<String, Object> parsedFilters;
}
