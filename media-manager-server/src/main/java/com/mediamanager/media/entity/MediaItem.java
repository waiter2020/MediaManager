package com.mediamanager.media.entity;

import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.library.entity.MediaLibrary;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "media_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(length = 512)
    private String title;

    @Column(name = "original_title", length = 512)
    private String originalTitle;

    @Column(name = "sort_title", length = 512)
    private String sortTitle;

    @Column(nullable = false, length = 16)
    private String type; // MOVIE, TV_SHOW, EPISODE, IMAGE, AUDIO

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "UNIDENTIFIED"; // IDENTIFIED, UNIDENTIFIED, ERROR

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "poster_path", length = 1024)
    private String posterPath;

    @Column(name = "backdrop_path", length = 1024)
    private String backdropPath;

    @Column(name = "provider_ids", columnDefinition = "text")
    private String providerIds; // JSON representation of TMDb/IMDb IDs

    @Column(name = "custom_fields", columnDefinition = "text")
    private String customFields; // JSON for any arbitrary metadata

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean hidden = false;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "media_item_tag",
        joinColumns = @JoinColumn(name = "media_item_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "media_item_category",
        joinColumns = @JoinColumn(name = "media_item_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();
}
