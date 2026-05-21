package com.mediamanager.metadata.dto;

import lombok.Data;

@Data
public class IdentifyRequest {
    private String provider = "tmdb";
    private String externalId;
}
