package com.mediamanager.metadata.entity;

import com.mediamanager.library.entity.MediaLibrary;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "scrape_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrapeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id")
    private MediaLibrary library;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "PENDING"; // PENDING, RUNNING, SUCCESS, FAILED, CANCELLED

    @Builder.Default
    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType = "MANUAL"; // MANUAL, SCHEDULED

    @Builder.Default
    @Column(name = "target_status", nullable = false, length = 16)
    private String targetStatus = "UNIDENTIFIED"; // UNIDENTIFIED, IDENTIFIED, ALL

    @Column(name = "media_types", columnDefinition = "TEXT")
    private String mediaTypes; // JSON array string, e.g. ["MOVIE","TV_SHOW"]

    @Column(name = "params_json", columnDefinition = "TEXT")
    private String paramsJson; // JSON object string, e.g. {"requestDelayMs":2000,"batchSize":50}

    @Builder.Default
    @Column(name = "total_items")
    private Integer totalItems = 0;

    @Builder.Default
    @Column(name = "scraped_items")
    private Integer scrapedItems = 0;

    @Builder.Default
    @Column(name = "error_items")
    private Integer errorItems = 0;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
