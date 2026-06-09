package com.mediamanager.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "media_post_process_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaPostProcessTask {

    public static final String TYPE_ITEM_FULL = "ITEM_FULL";
    public static final String TYPE_FILE_CHAPTERS = "FILE_CHAPTERS";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "task_type", nullable = false, length = 32)
    private String taskType;

    @Column(name = "media_item_id")
    private Integer mediaItemId;

    @Column(name = "media_file_id")
    private Integer mediaFileId;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Builder.Default
    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt = Instant.now();

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 32)
    private String source;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
