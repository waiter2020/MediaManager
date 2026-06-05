package com.mediamanager.media.repository;

import com.mediamanager.media.entity.UserPlaybackHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPlaybackHistoryRepository extends JpaRepository<UserPlaybackHistory, Integer> {

    List<UserPlaybackHistory> findByUserIdOrderByPlayedAtDesc(Integer userId, Pageable pageable);

    Optional<UserPlaybackHistory> findByUserIdAndMediaItemId(Integer userId, Integer mediaItemId);

    long countByUserId(Integer userId);

    @Query("SELECT COUNT(h) FROM UserPlaybackHistory h WHERE h.user.id = :userId AND h.completed = true")
    long countCompletedByUserId(@Param("userId") Integer userId);

    @Query("""
            SELECT COALESCE(SUM(
                CASE
                    WHEN h.durationSeconds IS NOT NULL AND h.durationSeconds > 0 THEN h.durationSeconds
                    ELSE COALESCE(h.position, 0)
                END
            ), 0)
            FROM UserPlaybackHistory h
            WHERE h.user.id = :userId
            """)
    Long sumPlaybackSecondsByUserId(@Param("userId") Integer userId);

    @Query("""
            SELECT h FROM UserPlaybackHistory h
            WHERE h.user.id = :userId
            ORDER BY h.playCount DESC, h.playedAt DESC
            """)
    List<UserPlaybackHistory> findTopPlayedByUserId(@Param("userId") Integer userId, Pageable pageable);

    @Query("SELECT h.mediaItem.id FROM UserPlaybackHistory h WHERE h.user.id = :userId AND h.completed = true")
    List<Integer> findCompletedMediaItemIdsByUserId(@Param("userId") Integer userId);
}
