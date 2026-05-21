package com.mediamanager.ai.repository;

import com.mediamanager.ai.entity.MediaEmbedding;
import com.mediamanager.ai.entity.MediaEmbeddingId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaEmbeddingRepository extends JpaRepository<MediaEmbedding, MediaEmbeddingId> {
    List<MediaEmbedding> findByModelId(String modelId);
}
