package com.mediamanager.ai.repository;

import com.mediamanager.ai.entity.AiSuggestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Integer> {

    @EntityGraph(attributePaths = {"mediaItem", "mediaItem.library"})
    List<AiSuggestion> findByReviewStatusOrderByCreatedAtDesc(String reviewStatus);
}
