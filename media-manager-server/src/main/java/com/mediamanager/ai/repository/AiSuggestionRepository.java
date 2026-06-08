package com.mediamanager.ai.repository;

import com.mediamanager.ai.entity.AiSuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Integer> {

    @EntityGraph(attributePaths = {"mediaItem", "mediaItem.library"})
    List<AiSuggestion> findByReviewStatusOrderByCreatedAtDesc(String reviewStatus);

    @EntityGraph(attributePaths = {"mediaItem", "mediaItem.library"})
    @Query("""
            SELECT s FROM AiSuggestion s
            JOIN s.mediaItem m
            WHERE s.reviewStatus = :reviewStatus
              AND m.library.id IN :libraryIds
              AND (m.hidden = false OR m.hidden IS NULL)
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    Page<AiSuggestion> findVisiblePendingPage(
            @Param("reviewStatus") String reviewStatus,
            @Param("libraryIds") Collection<Integer> libraryIds,
            Pageable pageable);

    @EntityGraph(attributePaths = {"mediaItem", "mediaItem.library"})
    @Query("""
            SELECT s FROM AiSuggestion s
            JOIN s.mediaItem m
            WHERE s.reviewStatus = :reviewStatus
              AND m.library.id IN :libraryIds
              AND (m.hidden = false OR m.hidden IS NULL)
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    List<AiSuggestion> findVisiblePendingBatch(
            @Param("reviewStatus") String reviewStatus,
            @Param("libraryIds") Collection<Integer> libraryIds,
            Pageable pageable);

    boolean existsByMediaItem_IdAndFieldNameAndSuggestedValueAndReviewStatus(
            Integer mediaItemId,
            String fieldName,
            String suggestedValue,
            String reviewStatus);

    @EntityGraph(attributePaths = {"mediaItem", "mediaItem.library"})
    List<AiSuggestion> findByReviewStatusAndMediaItem_IdInOrderByIdAsc(
            String reviewStatus,
            Collection<Integer> mediaItemIds);

    @EntityGraph(attributePaths = {"mediaItem", "mediaItem.library"})
    List<AiSuggestion> findByReviewStatusAndIdGreaterThanOrderByIdAsc(
            String reviewStatus,
            Integer id,
            Pageable pageable);
}
