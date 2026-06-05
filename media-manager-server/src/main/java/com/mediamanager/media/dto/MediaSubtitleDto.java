package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaSubtitleDto {
    private Integer id;
    private Integer mediaItemId;
    private Integer mediaFileId;
    private String fileName;
    private String language;
    private String format;
    private String title;
    private String source;
    private String provider;
    private String externalId;
    private Long fileSize;
    private Boolean defaultTrack;
    private Boolean forced;
}
