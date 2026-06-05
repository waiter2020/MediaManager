package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.MovieMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MovieMetadataRepository extends JpaRepository<MovieMetadata, Integer> {
    Optional<MovieMetadata> findByMediaItemId(Integer mediaItemId);
}
