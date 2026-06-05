package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ScrapeTaskResponse {

    private Integer id;
    private Long scheduleId;
    private Integer libraryId;
    private String libraryName;
    private String status;
    private String triggerType;
    private String targetStatus;
    private String mediaTypes;
    private String paramsJson;
    private Long requestDelayMs;
    private Integer batchSize;
    private Integer progressPercent;
    private Integer totalItems;
    private Integer scrapedItems;
    private Integer errorItems;
    private String errorLog;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;
}
