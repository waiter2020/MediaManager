package com.mediamanager.ai.repository;

import com.mediamanager.ai.entity.MediaEmbedding;
import com.mediamanager.ai.entity.MediaEmbeddingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaEmbeddingRepository extends JpaRepository<MediaEmbedding, MediaEmbeddingId> {
    List<MediaEmbedding> findByModelId(String modelId);
    long countByModelId(String modelId);

    @Query("SELECT e FROM MediaEmbedding e WHERE e.modelId = :modelId AND e.mediaItemId IN :itemIds")
    List<MediaEmbedding> findByModelIdAndMediaItemIdIn(
            @Param("modelId") String modelId,
            @Param("itemIds") Collection<Integer> itemIds);

    Optional<MediaEmbedding> findByMediaItemIdAndModelId(Integer mediaItemId, String modelId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MediaEmbedding")
    void deleteAllInBulk();
}
