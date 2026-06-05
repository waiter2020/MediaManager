package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class EpisodeDto {

    private Integer id;
    private Integer episodeNumber;
    private String title;
    private String overview;
    private LocalDate airDate;
    private Integer runtimeMinutes;
    private Float rating;
    private Integer mediaFileId;
    /** The MediaItem ID of the EPISODE item that owns {@code mediaFileId}. */
    private Integer mediaItemId;
}
