package com.mediamanager.library.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class MediaLibraryResponse {

    private Integer id;
    private String name;
    private String type;
    private String language;
    private Boolean autoScan;
    private Integer scanIntervalMinutes;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastScannedAt;
    private Long totalItems;

    private List<PathRes> paths;
    private List<ExtractorRes> extractors;

    @Data
    @Builder
    public static class PathRes {
        private Integer id;
        private String path;
        private Integer priority;
    }

    @Data
    @Builder
    public static class ExtractorRes {
        private Integer id;
        private String type;
        private Integer priority;
        private Boolean enabled;
        private String config;
    }
}
