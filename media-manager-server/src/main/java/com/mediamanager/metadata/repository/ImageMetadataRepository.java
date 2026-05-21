package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.ImageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageMetadataRepository extends JpaRepository<ImageMetadata, Integer> {

    Optional<ImageMetadata> findByMediaItemId(Integer mediaItemId);
}

