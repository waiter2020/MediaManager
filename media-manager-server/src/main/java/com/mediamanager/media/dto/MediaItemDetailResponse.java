package com.mediamanager.media.dto;

import com.mediamanager.metadata.dto.AudioMetadataDto;
import com.mediamanager.metadata.dto.ImageMetadataDto;
import com.mediamanager.metadata.dto.MovieMetadataDto;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class MediaItemDetailResponse {

    private Integer id;
    private Integer libraryId;
    private String title;
    private String originalTitle;
    private String type;
    private String status;
    private LocalDate releaseDate;
    private Float rating;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private Instant createdAt;
    private Instant updatedAt;

    private List<MediaFileDto> files;
    private List<TagDto> tags;
    private List<CategoryDto> categories;

    private MovieMetadataDto movieMetadata;
    private ImageMetadataDto imageMetadata;
    private AudioMetadataDto audioMetadata;
}

