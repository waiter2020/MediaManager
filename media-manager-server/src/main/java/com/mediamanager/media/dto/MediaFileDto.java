package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaFileDto {

    private Integer id;
    private Integer mediaItemId;
    private String mediaTitle;
    private String libraryName;
    private java.time.Instant deletedAt;
    private String filePath;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String container;
    private String videoCodec;
    private String audioCodec;
    private Integer width;
    private Integer height;
    private Integer durationSeconds;
    private Integer bitrate;
}

