package com.mediamanager.metadata.entity;

import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movie_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false, unique = true)
    private MediaItem mediaItem;

    @Column(length = 512)
    private String tagline;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(length = 16)
    private String certification;

    @Column(columnDefinition = "jsonb")
    private String genres; // JSON array of genre names

    @Column(columnDefinition = "jsonb")
    private String studios; // JSON array of studio names

    @Column(name = "cast_info", columnDefinition = "jsonb")
    private String castInfo; // JSON array: [{name, role, order, thumb}]

    @Column(columnDefinition = "jsonb")
    private String crew;

    @Column(name = "trailer_url", length = 1024)
    private String trailerUrl;
}
