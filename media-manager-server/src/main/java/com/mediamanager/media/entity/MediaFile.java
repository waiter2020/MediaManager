package com.mediamanager.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "media_file")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id")
    private MediaItem mediaItem;

    @Column(name = "file_path", nullable = false, unique = true, length = 2048)
    private String filePath;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(length = 16)
    private String container; // e.g. mkv, mp4, jpg

    @Column(name = "video_codec", length = 32)
    private String videoCodec; // e.g. h264, hevc

    @Column(name = "audio_codec", length = 32)
    private String audioCodec; // e.g. aac, ac3

    private Integer width;
    private Integer height;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private Integer bitrate;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "file_modified_at")
    private Instant fileModifiedAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean deleted = false; // Soft delete flag

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
