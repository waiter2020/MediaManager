package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaChapterDto {
    private Integer id;
    private Integer mediaFileId;
    private Integer chapterIndex;
    private String title;
    private Double startSeconds;
    private Double endSeconds;
    private String source;
    private Boolean thumbnailAvailable;
}
