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

    @Query("SELECT f FROM MediaFile f JOIN FETCH f.mediaItem i JOIN FETCH i.library WHERE f.id = :id")
    Optional<MediaFile> findByIdWithItemAndLibrary(@Param("id") Integer id);

    List<MediaFile> findByDeletedTrueOrderByDeletedAtDesc();

    @Query("SELECT f FROM MediaFile f JOIN FETCH f.mediaItem i JOIN FETCH i.library WHERE f.deleted = true ORDER BY f.deletedAt DESC")
    List<MediaFile> findDeletedWithItemAndLibrary();

    @Query("SELECT f FROM MediaFile f WHERE f.deleted = true AND f.deletedAt < :before")
    List<MediaFile> findDeletedBefore(@Param("before") java.time.Instant before);
}
