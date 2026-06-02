package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.Season;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Integer> {

    List<Season> findByTvShowMetadata_MediaItem_IdOrderBySeasonNumberAsc(Integer mediaItemId);

    Optional<Season> findByTvShowMetadata_MediaItem_IdAndSeasonNumber(Integer mediaItemId, Integer seasonNumber);
}
