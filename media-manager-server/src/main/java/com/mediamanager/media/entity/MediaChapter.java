package com.mediamanager.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "media_chapter",
        uniqueConstraints = @UniqueConstraint(columnNames = {"media_file_id", "chapter_index"}),
        indexes = {
                @Index(name = "idx_media_chapter_file_id", columnList = "media_file_id"),
                @Index(name = "idx_media_chapter_start", columnList = "media_file_id,start_seconds")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaChapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_file_id", nullable = false)
    private MediaFile mediaFile;

    @Column(name = "chapter_index", nullable = false)
    private Integer chapterIndex;

    @Column(length = 256)
    private String title;

    @Column(name = "start_seconds", nullable = false)
    private Double startSeconds;

    @Column(name = "end_seconds")
    private Double endSeconds;

    @Builder.Default
    @Column(nullable = false, length = 32)
    private String source = "EMBEDDED";

    @Column(name = "thumbnail_path", length = 2048)
    private String thumbnailPath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
