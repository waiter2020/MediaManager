package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaSubtitle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaSubtitleRepository extends JpaRepository<MediaSubtitle, Integer> {

    Optional<MediaSubtitle> findByFilePath(String filePath);

    List<MediaSubtitle> findByMediaItemIdOrderByLanguageAscFileNameAsc(Integer mediaItemId);

    List<MediaSubtitle> findByMediaFileIdOrderByLanguageAscFileNameAsc(Integer mediaFileId);

    List<MediaSubtitle> findByMediaFileIdAndSourceOrderByLanguageAscFileNameAsc(Integer mediaFileId, String source);

    boolean existsByMediaItemId(Integer mediaItemId);
}
