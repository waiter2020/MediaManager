package com.mediamanager.classification.repository;

import com.mediamanager.classification.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByParentId(Integer parentId);

    List<Category> findByType(String type);

    Optional<Category> findByParentIdAndName(Integer parentId, String name);

    interface CategoryUsageProjection {
        Integer getCategoryId();
        String getCategoryName();
        String getCategoryType();
        Long getUsageCount();
    }

    @Query(value = """
            SELECT
                c.id AS categoryId,
                c.name AS categoryName,
                c.type AS categoryType,
                COUNT(DISTINCT mic.media_item_id) AS usageCount
            FROM category c
            JOIN media_item_category mic ON mic.category_id = c.id
            JOIN media_item mi ON mi.id = mic.media_item_id
            WHERE (mi.hidden = FALSE OR mi.hidden IS NULL)
              AND mi.library_id IN (:libraryIds)
              AND c.type = :type
            GROUP BY c.id, c.name, c.type
            HAVING COUNT(DISTINCT mic.media_item_id) >= :minUsage
            ORDER BY COUNT(DISTINCT mic.media_item_id) DESC, lower(c.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<CategoryUsageProjection> findUsageCountsForLibraries(
            @Param("libraryIds") Collection<Integer> libraryIds,
            @Param("type") String type,
            @Param("minUsage") int minUsage,
            @Param("limit") int limit);
}
