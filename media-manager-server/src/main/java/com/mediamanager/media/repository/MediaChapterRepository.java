package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaChapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MediaChapterRepository extends JpaRepository<MediaChapter, Integer> {

    List<MediaChapter> findByMediaFileIdOrderByChapterIndexAsc(Integer mediaFileId);

    boolean existsByMediaFileId(Integer mediaFileId);

    @Query("""
            SELECT c FROM MediaChapter c
            JOIN FETCH c.mediaFile f
            WHERE f.mediaItem.id = :itemId AND f.deleted = false
            ORDER BY f.id ASC, c.chapterIndex ASC
            """)
    List<MediaChapter> findActiveByMediaItemId(@Param("itemId") Integer itemId);

    @Query("""
            SELECT c FROM MediaChapter c
            JOIN FETCH c.mediaFile f
            JOIN FETCH f.mediaItem i
            JOIN FETCH i.library
            WHERE c.id = :id
            """)
    Optional<MediaChapter> findByIdWithFileAndLibrary(@Param("id") Integer id);

    @Modifying
    @Transactional
    void deleteByMediaFileId(Integer mediaFileId);
}
