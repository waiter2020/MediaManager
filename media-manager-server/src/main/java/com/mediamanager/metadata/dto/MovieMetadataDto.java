package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MovieMetadataDto {

    private String tagline;
    private Integer runtimeMinutes;
    private String certification;
    private List<String> genres;
    private List<String> studios;
}

