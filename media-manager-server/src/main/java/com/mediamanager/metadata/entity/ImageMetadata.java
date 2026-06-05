package com.mediamanager.metadata.entity;

import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "image_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false, unique = true)
    private MediaItem mediaItem;

    private Integer width;
    private Integer height;

    @Column(name = "camera_make", length = 128)
    private String cameraMake;

    @Column(name = "camera_model", length = 128)
    private String cameraModel;

    @Column(length = 128)
    private String lens;

    @Column(length = 32)
    private String iso;

    @Column(length = 32)
    private String aperture;

    @Column(name = "shutter_speed", length = 32)
    private String shutterSpeed;

    @Column(name = "taken_at")
    private Instant takenAt;

    @Column(name = "gps_latitude")
    private Double gpsLatitude;

    @Column(name = "gps_longitude")
    private Double gpsLongitude;

    @Column(name = "exif_data", columnDefinition = "jsonb")
    private String exifData;
}
