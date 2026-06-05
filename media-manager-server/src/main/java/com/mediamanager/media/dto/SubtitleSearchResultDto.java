package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubtitleSearchResultDto {
    private String provider;
    private String externalId;
    private String title;
    private String language;
    private String format;
    private String releaseName;
    private String downloadUrl;
    private Float score;
}
