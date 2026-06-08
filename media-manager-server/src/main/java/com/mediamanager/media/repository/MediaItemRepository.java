package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MediaItemRepository extends JpaRepository<MediaItem, Integer>, JpaSpecificationExecutor<MediaItem> {
    long countByLibrary_Id(Integer libraryId);
    long countByType(String type);

    long countByHiddenFalse();

    long countByLibrary_IdAndHiddenFalse(Integer libraryId);

    @Query("""
            SELECT COUNT(m) FROM MediaItem m
            WHERE m.library.id = :libraryId
              AND (m.hidden = false OR m.hidden IS NULL)
            """)
    long countVisibleByLibraryId(@Param("libraryId") Integer libraryId);

    @Query("""
            SELECT COUNT(m) FROM MediaItem m
            WHERE m.library.id = :libraryId
              AND (m.hidden = false OR m.hidden IS NULL)
              AND m.type IN ('MOVIE', 'TV_SHOW', 'EPISODE')
            """)
    long countVisibleVideosByLibraryId(@Param("libraryId") Integer libraryId);

    long countByLibrary_IdAndTypeAndHiddenFalse(Integer libraryId, String type);

    long countByTypeAndHiddenFalse(String type);

    interface TypeUsageProjection {
        String getMediaType();
        Long getUsageCount();
    }

    @Query(value = """
            SELECT
                mi.type AS mediaType,
                COUNT(DISTINCT mi.id) AS usageCount
            FROM media_item mi
            WHERE (mi.hidden = FALSE OR mi.hidden IS NULL)
              AND mi.library_id IN (:libraryIds)
            GROUP BY mi.type
            HAVING COUNT(DISTINCT mi.id) >= :minUsage
            ORDER BY COUNT(DISTINCT mi.id) DESC, mi.type ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<TypeUsageProjection> findVisibleTypeUsageCounts(
            @Param("libraryIds") Collection<Integer> libraryIds,
            @Param("minUsage") int minUsage,
            @Param("limit") int limit);

    List<MediaItem> findByStatus(String status);
    List<MediaItem> findByLibraryIdAndStatus(Integer libraryId, String status);
    List<MediaItem> findByLibraryId(Integer libraryId);

    Optional<MediaItem> findFirstByLibrary_IdAndTypeAndOriginalTitleIgnoreCase(Integer libraryId, String type, String originalTitle);

    @Query("""
            SELECT m FROM MediaItem m
            WHERE m.library.id = :libraryId
              AND m.type = :type
              AND (lower(m.originalTitle) = lower(:name) OR lower(m.title) = lower(:name))
            ORDER BY m.id ASC
            """)
    List<MediaItem> findTitleCandidates(
            @Param("libraryId") Integer libraryId,
            @Param("type") String type,
            @Param("name") String name);

    // For background tasks (scrape/scan) where OpenSessionInView is disabled, we must prefetch library configs.
    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findByStatusOrderByIdAsc(String status);

    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findByLibraryIdAndStatusOrderByIdAsc(Integer libraryId, String status);

    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findByLibraryIdOrderByIdAsc(Integer libraryId);

    @EntityGraph(attributePaths = {"library", "library.extractorConfigs"})
    List<MediaItem> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = {"library"})
    @Query("""
            SELECT m FROM MediaItem m
            WHERE m.library.id = :libraryId
              AND (m.hidden = false OR m.hidden IS NULL)
              AND m.id > :afterId
            ORDER BY m.id ASC
            """)
    List<MediaItem> findVisibleForAiClassification(
            @Param("libraryId") Integer libraryId,
            @Param("afterId") Integer afterId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"library"})
    @Query("""
            SELECT m FROM MediaItem m
            WHERE m.library.id = :libraryId
              AND (m.hidden = false OR m.hidden IS NULL)
              AND m.type IN ('MOVIE', 'TV_SHOW', 'EPISODE')
              AND m.id > :afterId
            ORDER BY m.id ASC
            """)
    List<MediaItem> findVisibleVideosForAiClassification(
            @Param("libraryId") Integer libraryId,
            @Param("afterId") Integer afterId,
            Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"library"})
    Page<MediaItem> findAll(Specification<MediaItem> spec, Pageable pageable);

    @Query("SELECT m.id FROM MediaItem m WHERE (m.hidden = false OR m.hidden IS NULL) AND m.library.id IN :libraryIds")
    List<Integer> findVisibleIdsByLibraryIdsIn(@Param("libraryIds") Collection<Integer> libraryIds);

    @EntityGraph(attributePaths = {"library", "categories", "tags"})
    @Query("SELECT m FROM MediaItem m WHERE m.id = :id")
    Optional<MediaItem> findByIdWithClassificationGraph(@Param("id") Integer id);

    /** After library path migration/rescan, hidden rows may have a visible duplicate with the same title. */
    @Query("""
            SELECT m FROM MediaItem m
            WHERE m.library.id = :libraryId
              AND (m.hidden = false OR m.hidden IS NULL)
              AND lower(trim(m.title)) = lower(trim(:title))
              AND EXISTS (
                  SELECT 1 FROM MediaFile f
                  WHERE f.mediaItem = m AND (f.deleted = false OR f.deleted IS NULL)
              )
            ORDER BY m.id DESC
            """)
    List<MediaItem> findVisibleReplacementsByTitle(
            @Param("libraryId") Integer libraryId,
            @Param("title") String title);
}
