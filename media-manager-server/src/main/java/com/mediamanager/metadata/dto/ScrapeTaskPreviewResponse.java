package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ScrapeTaskPreviewResponse {

    private Integer libraryId;
    private String libraryName;
    private String targetStatus;
    private List<String> mediaTypes;
    private Integer totalItems;
    private Integer allVisibleItems;
    private Map<String, Integer> byStatus;
    private Map<String, Integer> byType;
    private List<String> enabledScrapers;
    private List<String> tips;
}
