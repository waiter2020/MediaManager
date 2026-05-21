package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AudioMetadataDto {

    private String artist;
    private String album;
    private String albumArtist;
    private Integer trackNumber;
    private Integer discNumber;
    private List<String> genres;
    private Integer durationSeconds;
    private Integer bitrate;
    private Integer sampleRate;
    private Integer channels;
}

