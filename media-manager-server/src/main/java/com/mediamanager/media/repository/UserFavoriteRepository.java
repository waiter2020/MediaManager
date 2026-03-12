package com.mediamanager.media.repository;

import com.mediamanager.media.entity.UserFavorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Integer> {

    List<UserFavorite> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    Optional<UserFavorite> findByUserIdAndMediaItemId(Integer userId, Integer mediaItemId);

    boolean existsByUserIdAndMediaItemId(Integer userId, Integer mediaItemId);

    long countByUserId(Integer userId);
}
