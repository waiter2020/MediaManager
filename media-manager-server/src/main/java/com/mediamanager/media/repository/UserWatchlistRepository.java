package com.mediamanager.media.repository;

import com.mediamanager.media.entity.UserWatchlist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, Integer> {

    List<UserWatchlist> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    Optional<UserWatchlist> findByUserIdAndMediaItemId(Integer userId, Integer mediaItemId);

    boolean existsByUserIdAndMediaItemId(Integer userId, Integer mediaItemId);

    long countByUserId(Integer userId);
}
