package com.mediamanager.metadata.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "season")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tv_show_metadata_id", nullable = false)
    private TvShowMetadata tvShowMetadata;

    @Column(name = "season_number")
    private Integer seasonNumber;

    @Column(length = 256)
    private String name;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "poster_path", length = 1024)
    private String posterPath;

    @Builder.Default
    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Episode> episodes = new ArrayList<>();
}
