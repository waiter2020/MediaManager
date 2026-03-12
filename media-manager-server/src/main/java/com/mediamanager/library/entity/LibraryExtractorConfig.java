package com.mediamanager.library.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "library_extractor_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryExtractorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(name = "extractor_type", nullable = false, length = 32)
    private String extractorType; // NFO, FFPROBE, TMDB, EXIF, etc.

    @Builder.Default
    private Integer priority = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(columnDefinition = "TEXT")
    private String config; // JSON payload for specific extractor config (e.g. Tmdb API Key)
}
