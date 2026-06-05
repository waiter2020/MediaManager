package com.mediamanager.metadata.entity;

import com.mediamanager.library.entity.MediaLibrary;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "scrape_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrapeSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 128)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "schedule_type", nullable = false, length = 16)
    private String scheduleType; // CRON | FIXED_DELAY

    @Column(name = "cron_expr", length = 128)
    private String cronExpr;

    @Column(name = "interval_seconds")
    private Integer intervalSeconds;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String scope = "GLOBAL"; // GLOBAL | LIBRARY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id")
    private MediaLibrary library;

    @Builder.Default
    @Column(name = "target_status", nullable = false, length = 16)
    private String targetStatus = "UNIDENTIFIED"; // UNIDENTIFIED | IDENTIFIED | ALL

    @Column(name = "media_types", columnDefinition = "TEXT")
    private String mediaTypes; // JSON array string

    @Builder.Default
    @Column(name = "max_concurrency", nullable = false)
    private Integer maxConcurrency = 1;

    @Column(name = "batch_size_override")
    private Integer batchSizeOverride;

    @Column(name = "request_delay_ms_override")
    private Integer requestDelayMsOverride;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_status", length = 16)
    private String lastStatus;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

