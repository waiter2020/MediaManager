package com.mediamanager.media.repository;

import com.mediamanager.media.entity.UserPlaybackHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPlaybackHistoryRepository extends JpaRepository<UserPlaybackHistory, Integer> {

    List<UserPlaybackHistory> findByUserIdOrderByPlayedAtDesc(Integer userId, Pageable pageable);

    Optional<UserPlaybackHistory> findByUserIdAndMediaItemId(Integer userId, Integer mediaItemId);

    long countByUserId(Integer userId);
}
