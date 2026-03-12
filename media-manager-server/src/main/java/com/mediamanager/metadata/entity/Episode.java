package com.mediamanager.metadata.entity;

import com.mediamanager.media.entity.MediaFile;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "episode")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_file_id", unique = true)
    private MediaFile mediaFile;

    @Column(name = "episode_number")
    private Integer episodeNumber;

    @Column(length = 512)
    private String title;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "air_date")
    private LocalDate airDate;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    private Double rating;
}
