package com.mediamanager.metadata.entity;

import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audio_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false, unique = true)
    private MediaItem mediaItem;

    @Column(length = 256)
    private String artist;

    @Column(length = 256)
    private String album;

    @Column(name = "album_artist", length = 256)
    private String albumArtist;

    @Column(name = "track_number")
    private Integer trackNumber;

    @Column(name = "disc_number")
    private Integer discNumber;

    @Column(columnDefinition = "jsonb")
    private String genres;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private Integer bitrate;
    
    @Column(name = "sample_rate")
    private Integer sampleRate;
    
    private Integer channels;
}
