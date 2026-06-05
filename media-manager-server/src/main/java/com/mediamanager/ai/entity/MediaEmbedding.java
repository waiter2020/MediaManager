package com.mediamanager.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "media_embedding")
@IdClass(MediaEmbeddingId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaEmbedding {

    @Id
    @Column(name = "media_item_id")
    private Integer mediaItemId;

    @Id
    @Column(name = "model_id", length = 64)
    private String modelId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] vector;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
