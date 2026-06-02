package com.mediamanager.search.dto;

import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedSearchResult {
    private PageResult<MediaItemResponse> results;
    private Map<String, Object> parsedFilters;
    private List<String> sources;
    private String hint;
}
