package com.mediamanager.metadata.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageMetadataDto {

    private Integer width;
    private Integer height;
    private String cameraMake;
    private String cameraModel;
    private String lens;
    private String iso;
    private String aperture;
    private String shutterSpeed;
    private String takenAt;
    private Double gpsLatitude;
    private Double gpsLongitude;
}

