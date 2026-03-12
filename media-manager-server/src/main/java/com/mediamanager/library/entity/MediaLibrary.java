package com.mediamanager.library.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "media_library")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String type; // MOVIE, TV_SHOW, IMAGE, AUDIO

    @Builder.Default
    @Column(length = 8)
    private String language = "zh";

    @Builder.Default
    @Column(name = "auto_scan", nullable = false)
    private Boolean autoScan = true;

    @Builder.Default
    @Column(name = "scan_interval_minutes")
    private Integer scanIntervalMinutes = 30;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Builder.Default
    @OneToMany(mappedBy = "library", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LibraryPath> paths = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "library", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LibraryExtractorConfig> extractorConfigs = new LinkedHashSet<>();

    public void addPath(LibraryPath path) {
        paths.add(path);
        path.setLibrary(this);
    }

    public void addExtractorConfig(LibraryExtractorConfig config) {
        extractorConfigs.add(config);
        config.setLibrary(this);
    }
}
