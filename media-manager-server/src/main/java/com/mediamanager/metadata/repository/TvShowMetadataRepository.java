package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.TvShowMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TvShowMetadataRepository extends JpaRepository<TvShowMetadata, Integer> {
    Optional<TvShowMetadata> findByMediaItemId(Integer mediaItemId);
}
