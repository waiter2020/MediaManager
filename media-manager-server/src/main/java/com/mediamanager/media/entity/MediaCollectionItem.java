package com.mediamanager.media.entity;

import com.mediamanager.common.jpa.InstantMillisAttributeConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "media_collection_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaCollectionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private MediaCollection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @Builder.Default
    @Column(nullable = false)
    private Integer position = 0;

    @CreationTimestamp
    @Convert(converter = InstantMillisAttributeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
