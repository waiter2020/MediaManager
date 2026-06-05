package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SeasonDto {

    private Integer id;
    private Integer seasonNumber;
    private String name;
    private String overview;
    private String posterPath;
    private List<EpisodeDto> episodes;
}
