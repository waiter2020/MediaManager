package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TvShowMetadataDto {

    private String status;
    private String network;
    private List<String> genres;
}
