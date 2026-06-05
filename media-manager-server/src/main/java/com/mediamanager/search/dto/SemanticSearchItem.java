package com.mediamanager.search.dto;

import com.mediamanager.media.dto.MediaItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticSearchItem {
    private MediaItemResponse item;
    private Float score;
}
