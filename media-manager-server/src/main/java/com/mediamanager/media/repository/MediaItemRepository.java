package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MediaItemRepository extends JpaRepository<MediaItem, Integer>, JpaSpecificationExecutor<MediaItem> {
    long countByLibrary_Id(Integer libraryId);
    long countByType(String type);
}
