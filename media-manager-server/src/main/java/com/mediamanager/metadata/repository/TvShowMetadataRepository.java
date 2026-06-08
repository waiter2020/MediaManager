package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.TvShowMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TvShowMetadataRepository extends JpaRepository<TvShowMetadata, Integer> {
    Optional<TvShowMetadata> findByMediaItemId(Integer mediaItemId);

    @Query("""
            SELECT metadata FROM TvShowMetadata metadata
            JOIN FETCH metadata.mediaItem item
            WHERE item.library.id IN :libraryIds
              AND (item.hidden = false OR item.hidden IS NULL)
            """)
    List<TvShowMetadata> findVisibleByLibraryIds(@Param("libraryIds") Collection<Integer> libraryIds);
}
