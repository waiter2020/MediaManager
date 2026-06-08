package com.mediamanager.classification.repository;

import com.mediamanager.classification.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Integer> {
    Optional<Tag> findByName(String name);

    @Query("SELECT t.id FROM Tag t ORDER BY t.id ASC")
    List<Integer> findAllIds();

    interface TagUsageProjection {
        Integer getTagId();
        String getTagName();
        String getColor();
        String getSource();
        Long getUsageCount();
        Instant getCreatedAt();
    }

    @Query(value = """
            SELECT
                t.id AS tagId,
                t.name AS tagName,
                t.color AS color,
                t.source AS source,
                COUNT(DISTINCT mit.media_item_id) AS usageCount,
                t.created_at AS createdAt
            FROM tag t
            LEFT JOIN media_item_tag mit ON mit.tag_id = t.id
            GROUP BY t.id, t.name, t.color, t.source, t.created_at
            ORDER BY lower(t.name) ASC
            """, nativeQuery = true)
    List<TagUsageProjection> findGlobalUsageCounts();

    @Query(value = """
            SELECT
                t.id AS tagId,
                t.name AS tagName,
                t.color AS color,
                t.source AS source,
                COUNT(DISTINCT mit.media_item_id) AS usageCount,
                t.created_at AS createdAt
            FROM tag t
            JOIN media_item_tag mit ON mit.tag_id = t.id
            JOIN media_item mi ON mi.id = mit.media_item_id
            WHERE (mi.hidden = FALSE OR mi.hidden IS NULL)
              AND mi.library_id IN (:libraryIds)
            GROUP BY t.id, t.name, t.color, t.source, t.created_at
            HAVING COUNT(DISTINCT mit.media_item_id) >= :minUsage
            ORDER BY COUNT(DISTINCT mit.media_item_id) DESC, lower(t.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<TagUsageProjection> findTopUsageCountsForLibraries(
            @Param("libraryIds") Collection<Integer> libraryIds,
            @Param("minUsage") int minUsage,
            @Param("limit") int limit);
}
