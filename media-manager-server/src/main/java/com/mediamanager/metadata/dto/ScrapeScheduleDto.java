package com.mediamanager.metadata.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ScrapeScheduleDto {

    private Integer id;

    @NotBlank(message = "name is required")
    private String name;

    private Boolean enabled;

    @NotBlank(message = "scheduleType is required")
    @Pattern(regexp = "CRON|FIXED_DELAY", message = "scheduleType must be CRON or FIXED_DELAY")
    private String scheduleType;

    private String cronExpr;
    private Integer intervalSeconds;

    @NotBlank(message = "scope is required")
    @Pattern(regexp = "GLOBAL|LIBRARY", message = "scope must be GLOBAL or LIBRARY")
    private String scope;

    private Integer libraryId;

    @NotBlank(message = "targetStatus is required")
    @Pattern(regexp = "UNIDENTIFIED|IDENTIFIED|ALL", message = "targetStatus must be UNIDENTIFIED, IDENTIFIED or ALL")
    private String targetStatus;

    /** JSON array string, e.g. ["MOVIE","TV_SHOW"] */
    private String mediaTypes;

    @NotNull(message = "maxConcurrency is required")
    @Min(value = 1, message = "maxConcurrency must be >= 1")
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
