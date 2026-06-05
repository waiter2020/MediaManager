package com.mediamanager.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "media_subtitle",
        indexes = {
                @Index(name = "idx_media_subtitle_item_id", columnList = "media_item_id"),
                @Index(name = "idx_media_subtitle_file_id", columnList = "media_file_id"),
                @Index(name = "idx_media_subtitle_language", columnList = "language")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaSubtitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_file_id")
    private MediaFile mediaFile;

    @Column(name = "file_path", nullable = false, unique = true, length = 2048)
    private String filePath;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(length = 32)
    private String language;

    @Column(length = 16)
    private String format;

    @Column(length = 256)
    private String title;

    @Builder.Default
    @Column(nullable = false, length = 32)
    private String source = "LOCAL";

    @Column(length = 64)
    private String provider;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_modified_at")
    private Instant fileModifiedAt;

    @Builder.Default
    @Column(name = "default_track", nullable = false)
    private Boolean defaultTrack = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean forced = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
