package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaCollectionItem;
import com.mediamanager.media.entity.MediaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MediaCollectionItemRepository extends JpaRepository<MediaCollectionItem, Integer> {

    List<MediaCollectionItem> findByCollectionIdOrderByPositionAscCreatedAtAsc(Integer collectionId);

    @Query(
            value = """
                    SELECT i.mediaItem FROM MediaCollectionItem i
                    WHERE i.collection.id = :collectionId
                      AND (i.mediaItem.hidden = false OR i.mediaItem.hidden IS NULL)
                      AND i.mediaItem.library.id IN :libraryIds
                    ORDER BY i.position ASC, i.createdAt ASC
                    """,
            countQuery = """
                    SELECT COUNT(i) FROM MediaCollectionItem i
                    WHERE i.collection.id = :collectionId
                      AND (i.mediaItem.hidden = false OR i.mediaItem.hidden IS NULL)
                      AND i.mediaItem.library.id IN :libraryIds
                    """)
    Page<MediaItem> findVisibleMediaItems(
            @Param("collectionId") Integer collectionId,
            @Param("libraryIds") Set<Integer> libraryIds,
            Pageable pageable);

    Optional<MediaCollectionItem> findByCollectionIdAndMediaItemId(Integer collectionId, Integer mediaItemId);

    boolean existsByCollectionIdAndMediaItemId(Integer collectionId, Integer mediaItemId);

    long countByCollectionId(Integer collectionId);

    void deleteByCollectionIdAndMediaItemId(Integer collectionId, Integer mediaItemId);

    @Query("SELECT COALESCE(MAX(i.position), -1) FROM MediaCollectionItem i WHERE i.collection.id = :collectionId")
    Integer findMaxPosition(@Param("collectionId") Integer collectionId);
}
