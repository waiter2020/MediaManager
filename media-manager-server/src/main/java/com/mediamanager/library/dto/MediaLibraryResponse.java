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
    /**
     * @deprecated Derived from {@link #plugins} (EXTRACTOR kind only). Prefer {@code plugins[]}.
     *             Legacy PUT {@code extractors[]} on library still syncs into {@code library_plugin_config}.
     */
    @Deprecated
    private List<ExtractorRes> extractors;
    /** Unified library_plugin_config rows (preferred). */
    private List<PluginRes> plugins;

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

    @Data
    @Builder
    public static class PluginRes {
        private String pluginId;
        private String kind;
        private Boolean enabled;
        private Integer priority;
        private String config;
    }
}
