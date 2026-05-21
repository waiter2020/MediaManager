package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ScrapeScheduleDto {
    private Integer id;
    private String name;
    private Boolean enabled;
    private String scheduleType;
    private String cronExpr;
    private Integer intervalSeconds;
    private String scope;
    private Integer libraryId;
    private String targetStatus;
    private String mediaTypes;
    private Integer maxConcurrency;
    private Integer batchSizeOverride;
    private Integer requestDelayMsOverride;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private String lastStatus;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}

