package com.mediamanager.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubtitleDownloadRequest {
    @NotBlank
    private String provider;
    @NotBlank
    private String externalId;
    private Integer fileId;
    private String language;
}
