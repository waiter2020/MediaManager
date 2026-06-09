package com.mediamanager.library.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LibraryScanRequest {

    private Boolean refreshMetadata = false;

    private Boolean scanMissingMetadata = false;

    private Boolean reconcileMissing = true;

    private Boolean scrapeAfterScan = false;

    @Pattern(regexp = "UNIDENTIFIED|IDENTIFIED|ALL", message = "scrapeTargetStatus must be UNIDENTIFIED, IDENTIFIED or ALL")
    private String scrapeTargetStatus = "UNIDENTIFIED";

    private Boolean skipPostProcess = false;
}
