package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.AudioMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AudioMetadataRepository extends JpaRepository<AudioMetadata, Integer> {

    Optional<AudioMetadata> findByMediaItemId(Integer mediaItemId);
}

