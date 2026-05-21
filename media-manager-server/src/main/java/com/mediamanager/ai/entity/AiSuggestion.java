package com.mediamanager.ai.entity;

import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_suggestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "suggested_value", columnDefinition = "TEXT")
    private String suggestedValue;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    private Float confidence;

    @Builder.Default
    @Column(name = "review_status", length = 16)
    private String reviewStatus = "PENDING";

    @Column(name = "reviewed_by")
    private Integer reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
