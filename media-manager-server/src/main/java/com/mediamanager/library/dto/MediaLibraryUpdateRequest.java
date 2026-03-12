package com.mediamanager.library.dto;

import lombok.Data;

import java.util.List;

@Data
public class MediaLibraryUpdateRequest {

    private String name;

    private String type;

    private String language;

    private Boolean autoScan;

    private Integer scanIntervalMinutes;

    private List<PathReq> paths;

    private List<ExtractorReq> extractors;

    @Data
    public static class PathReq {
        private String path;
        private Integer priority = 0;
    }

    @Data
    public static class ExtractorReq {
        private String type;
        private Integer priority = 0;
        private Boolean enabled = true;
        private String config;
    }
}
