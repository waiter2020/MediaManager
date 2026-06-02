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
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserPlaybackHistoryRepository playbackRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MediaFileRepository fileRepository;
    private final SysUserRepository userRepository;
    private final LibraryAccessService libraryAccessService;

    @Transactional
    public void recordPlayback(Integer userId, Integer mediaItemId, Integer position) {
        MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
        libraryAccessService.assertCanViewItem(item);

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
        MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
        libraryAccessService.assertCanViewItem(item);

        var existing = favoriteRepository.findByUserIdAndMediaItemId(userId, mediaItemId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        } else {
            SysUser user = userRepository.findById(userId).orElseThrow();
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
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(
                Optional.of(userRepository.findById(userId).orElseThrow()));
        List<UserPlaybackHistory> records = playbackRepository
                .findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, limit));
        return records.stream()
                .filter(rec -> isVisible(rec.getMediaItem(), viewableLibraryIds))
                .map(rec -> {
                    MediaItemResponse resp = toResponse(rec.getMediaItem());
                    resp.setPlaybackPosition(rec.getPosition());
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> getRecentFavorites(Integer userId, int limit) {
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(
                Optional.of(userRepository.findById(userId).orElseThrow()));
        List<UserFavorite> records = favoriteRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
        return records.stream()
                .map(UserFavorite::getMediaItem)
                .filter(item -> isVisible(item, viewableLibraryIds))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private boolean isVisible(MediaItem item, Set<Integer> viewableLibraryIds) {
        return item != null
                && !Boolean.TRUE.equals(item.getHidden())
                && item.getLibrary() != null
                && viewableLibraryIds.contains(item.getLibrary().getId());
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
