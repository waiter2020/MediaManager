package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends JpaRepository<MediaFile, Integer> {
    Optional<MediaFile> findByFilePath(String filePath);

    @Query("SELECT COUNT(m) > 0 FROM MediaFile m WHERE m.filePath = :filePath AND m.deleted = false")
    boolean existsByFilePathAndNotDeleted(@Param("filePath") String filePath);

    List<MediaFile> findByMediaItemId(Integer mediaItemId);

    List<MediaFile> findByMediaItemIdAndDeletedFalse(Integer mediaItemId);
}
