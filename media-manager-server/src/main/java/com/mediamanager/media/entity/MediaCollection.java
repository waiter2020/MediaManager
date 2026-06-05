package com.mediamanager.media.entity;

import com.mediamanager.common.jpa.InstantMillisAttributeConverter;
import com.mediamanager.system.entity.SysUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "media_collection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private SysUser owner;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String type = "COLLECTION";

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String visibility = "PRIVATE";

    @Column(name = "poster_path", length = 1024)
    private String posterPath;

    @Builder.Default
    @Column(nullable = false)
    private Boolean smart = false;

    @Column(name = "rule_json", columnDefinition = "text")
    private String ruleJson;

    @CreationTimestamp
    @Convert(converter = InstantMillisAttributeConverter.class)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Convert(converter = InstantMillisAttributeConverter.class)
    @Column(name = "updated_at")
    private Instant updatedAt;
}
