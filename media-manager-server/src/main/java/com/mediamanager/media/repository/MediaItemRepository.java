package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MediaItemRepository extends JpaRepository<MediaItem, Integer>, JpaSpecificationExecutor<MediaItem> {
    long countByLibrary_Id(Integer libraryId);
    long countByType(String type);

    long countByHiddenFalse();

    long countByLibrary_IdAndHiddenFalse(Integer libraryId);

    long countByTypeAndHiddenFalse(String type);

    List<MediaItem> findByStatus(String status);
    List<MediaItem> findByLibraryIdAndStatus(Integer libraryId, String status);
    List<MediaItem> findByLibraryId(Integer libraryId);

    // For background tasks (scrape/scan) where OpenSessionInView is disabled, we must prefetch library configs.
    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findByStatusOrderByIdAsc(String status);

    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findByLibraryIdAndStatusOrderByIdAsc(Integer libraryId, String status);

    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findByLibraryIdOrderByIdAsc(Integer libraryId);

    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findAllByOrderByIdAsc();

    @Override
    @EntityGraph(attributePaths = {"library"})
    Page<MediaItem> findAll(Specification<MediaItem> spec, Pageable pageable);
}
