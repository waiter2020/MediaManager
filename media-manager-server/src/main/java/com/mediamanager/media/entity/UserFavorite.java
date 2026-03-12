package com.mediamanager.media.entity;

import com.mediamanager.common.jpa.InstantMillisAttributeConverter;
import com.mediamanager.system.entity.SysUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_favorite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private SysUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_item_id", nullable = false)
    private MediaItem mediaItem;

    @CreationTimestamp
    @Convert(converter = InstantMillisAttributeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
