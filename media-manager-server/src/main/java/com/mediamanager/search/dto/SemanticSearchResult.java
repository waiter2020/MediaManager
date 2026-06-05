package com.mediamanager.search.dto;

import com.mediamanager.media.dto.MediaItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchResult {
    private List<MediaItemResponse> items;
    private List<SemanticSearchItem> scoredItems;
    private String hint;
}
