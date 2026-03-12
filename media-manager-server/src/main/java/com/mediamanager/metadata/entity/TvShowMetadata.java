package com.mediamanager.metadata.entity;

import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tv_show_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TvShowMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false, unique = true)
    private MediaItem mediaItem;

    @Column(length = 16)
    private String status; // Continuing, Ended

    @Column(length = 128)
    private String network;

    @Column(columnDefinition = "jsonb")
    private String genres;

    @Column(name = "cast_info", columnDefinition = "jsonb")
    private String castInfo;
}
