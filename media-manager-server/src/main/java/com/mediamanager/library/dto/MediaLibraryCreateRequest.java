package com.mediamanager.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MediaLibraryCreateRequest {

    @NotBlank(message = "Library name cannot be empty")
    private String name;

    @NotBlank(message = "Library type cannot be empty")
    private String type; // MOVIE, TV_SHOW, IMAGE, AUDIO

    private String language = "zh";

    private Boolean autoScan = true;

    private Integer scanIntervalMinutes = 30;

    @NotEmpty(message = "At least one path is required")
    private List<PathReq> paths;

    /** Optional; when omitted the server seeds plugins from {@code type}. */
    private List<ExtractorReq> extractors;

    @Data
    public static class PathReq {
        @NotBlank(message = "Path cannot be empty")
        private String path;
        private Integer priority = 0;
    }

    @Data
    public static class ExtractorReq {
        @NotBlank(message = "Extractor type cannot be empty")
        private String type;
        private Integer priority = 0;
        private Boolean enabled = true;
        private String config; // JSON string
    }
}
