package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.ImageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImageMetadataRepository extends JpaRepository<ImageMetadata, Integer> {

    Optional<ImageMetadata> findByMediaItemId(Integer mediaItemId);

    @Query("""
            SELECT metadata FROM ImageMetadata metadata
            JOIN FETCH metadata.mediaItem item
            WHERE item.library.id IN :libraryIds
              AND (item.hidden = false OR item.hidden IS NULL)
            """)
    List<ImageMetadata> findVisibleByLibraryIds(@Param("libraryIds") Collection<Integer> libraryIds);
}
