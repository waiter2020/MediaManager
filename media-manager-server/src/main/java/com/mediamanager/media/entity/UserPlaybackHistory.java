package com.mediamanager.media.entity;

import com.mediamanager.common.jpa.InstantMillisAttributeConverter;
import com.mediamanager.system.entity.SysUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_playback_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPlaybackHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private SysUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Convert(converter = InstantMillisAttributeConverter.class)
    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @Builder.Default
    @Column(name = "position")
    private Integer position = 0;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Builder.Default
    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @Convert(converter = InstantMillisAttributeConverter.class)
    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Column(name = "play_count", nullable = false)
    private Integer playCount = 1;
}
