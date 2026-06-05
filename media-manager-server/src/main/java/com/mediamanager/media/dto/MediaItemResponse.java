package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MediaItemResponse {
    private Integer id;
    private Integer libraryId;
    private String libraryName;
    private String title;
    private String type;
    private String status;
    private LocalDate releaseDate;
    private Float rating;
    private String overview;
    private String posterPath;
    private List<Integer> fileIds;
    private List<TagDto> tags;
    private List<CategoryDto> categories;
    /** Seconds from start when returned from playback history (continue watching). */
    private Integer playbackPosition;
    private Integer playbackDuration;
    private Double playbackPercent;
    private Boolean watched;
    private Boolean favorited;
    private Boolean watchlisted;
}
