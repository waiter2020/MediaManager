package com.mediamanager.media.service;

import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.entity.UserFavorite;
import com.mediamanager.media.entity.UserPlaybackHistory;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.UserFavoriteRepository;
import com.mediamanager.media.repository.UserPlaybackHistoryRepository;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserPlaybackHistoryRepository playbackRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MediaFileRepository fileRepository;
    private final SysUserRepository userRepository;

    @Transactional
    public void recordPlayback(Integer userId, Integer mediaItemId, Integer position) {
        var existing = playbackRepository.findByUserIdAndMediaItemId(userId, mediaItemId);
        if (existing.isPresent()) {
            UserPlaybackHistory record = existing.get();
            record.setPlayedAt(Instant.now());
            if (position != null) {
                record.setPosition(position);
            }
            playbackRepository.save(record);
        } else {
            SysUser user = userRepository.findById(userId).orElseThrow();
            MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
            UserPlaybackHistory record = UserPlaybackHistory.builder()
                    .user(user)
                    .mediaItem(item)
                    .playedAt(Instant.now())
                    .position(position != null ? position : 0)
                    .build();
            playbackRepository.save(record);
        }
    }

    @Transactional
    public boolean toggleFavorite(Integer userId, Integer mediaItemId) {
        var existing = favoriteRepository.findByUserIdAndMediaItemId(userId, mediaItemId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        } else {
            SysUser user = userRepository.findById(userId).orElseThrow();
            MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
            UserFavorite fav = UserFavorite.builder()
                    .user(user)
                    .mediaItem(item)
                    .build();
            favoriteRepository.save(fav);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(Integer userId, Integer mediaItemId) {
        return favoriteRepository.existsByUserIdAndMediaItemId(userId, mediaItemId);
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> getRecentPlayed(Integer userId, int limit) {
        List<UserPlaybackHistory> records = playbackRepository
                .findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, limit));
        return records.stream()
                .map(UserPlaybackHistory::getMediaItem)
                .filter(this::isVisible)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> getRecentFavorites(Integer userId, int limit) {
        List<UserFavorite> records = favoriteRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
        return records.stream()
                .map(UserFavorite::getMediaItem)
                .filter(this::isVisible)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private boolean isVisible(MediaItem item) {
        return item != null
                && !Boolean.TRUE.equals(item.getHidden())
                && item.getLibrary() != null;
    }

    private MediaItemResponse toResponse(MediaItem item) {
        Float rating = item.getRating() != null ? item.getRating().floatValue() : null;

        List<Integer> fileIds = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .map(MediaFile::getId)
                .collect(Collectors.toList());

        return MediaItemResponse.builder()
                .id(item.getId())
                .libraryId(item.getLibrary().getId())
                .libraryName(item.getLibrary().getName())
                .title(item.getTitle())
                .type(item.getType())
                .status(item.getStatus())
                .releaseDate(item.getReleaseDate())
                .rating(rating)
                .overview(item.getOverview())
                .posterPath(item.getPosterPath())
                .fileIds(fileIds)
                .build();
    }
}
