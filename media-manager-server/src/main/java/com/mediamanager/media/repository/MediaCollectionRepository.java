package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaCollection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MediaCollectionRepository extends JpaRepository<MediaCollection, Integer> {

    boolean existsByOwner_IdAndNameIgnoreCase(Integer ownerId, String name);

    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT c FROM MediaCollection c
            WHERE c.visibility = 'SHARED'
               OR (:userId IS NOT NULL AND c.owner.id = :userId)
            ORDER BY c.updatedAt DESC NULLS LAST, c.createdAt DESC
            """)
    List<MediaCollection> findVisibleToUser(@Param("userId") Integer userId);
}
