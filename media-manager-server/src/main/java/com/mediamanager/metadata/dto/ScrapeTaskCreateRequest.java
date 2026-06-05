package com.mediamanager.metadata.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class ScrapeTaskCreateRequest {

    private Integer libraryId;

    @Pattern(regexp = "UNIDENTIFIED|IDENTIFIED|ALL", message = "targetStatus must be UNIDENTIFIED, IDENTIFIED or ALL")
    private String targetStatus = "UNIDENTIFIED";

    private List<String> mediaTypes;

    @Min(value = 0, message = "requestDelayMs must be >= 0")
    private Long requestDelayMs;

    @Min(value = 1, message = "batchSize must be >= 1")
    private Integer batchSize;
}
