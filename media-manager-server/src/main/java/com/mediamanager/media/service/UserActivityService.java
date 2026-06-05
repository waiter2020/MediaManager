package com.mediamanager.media.service;

import com.mediamanager.media.dto.CategoryDto;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.dto.TagDto;
import com.mediamanager.media.dto.PlaybackStatsResponse;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.entity.UserFavorite;
import com.mediamanager.media.entity.UserPlaybackHistory;
import com.mediamanager.media.entity.UserWatchlist;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.UserFavoriteRepository;
import com.mediamanager.media.repository.UserPlaybackHistoryRepository;
import com.mediamanager.media.repository.UserWatchlistRepository;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.repository.SysUserRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
    private final UserWatchlistRepository watchlistRepository;

    @Transactional
    public void recordPlayback(Integer userId, Integer mediaItemId, Integer position) {
        recordPlayback(userId, mediaItemId, position, null, null);
    }

    @Transactional
    public void recordPlayback(
            Integer userId,
            Integer mediaItemId,
            Integer position,
            Integer durationSeconds,
            Boolean completed) {
        MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
        libraryAccessService.assertCanViewItem(item);

        Integer resolvedDuration = resolveDurationSeconds(item, durationSeconds);
        Integer resolvedPosition = position != null ? Math.max(position, 0) : 0;
        boolean completedValue = resolveCompleted(completed, resolvedPosition, resolvedDuration);
        Instant now = Instant.now();

        var existing = playbackRepository.findByUserIdAndMediaItemId(userId, mediaItemId);
        if (existing.isPresent()) {
            UserPlaybackHistory record = existing.get();
            Integer previousPosition = record.getPosition();
            record.setPlayedAt(now);
            if (position != null) {
                record.setPosition(resolvedPosition);
            }
            if (resolvedDuration != null) {
                record.setDurationSeconds(resolvedDuration);
            }
            record.setCompleted(completedValue);
            record.setCompletedAt(completedValue ? now : null);
            if (shouldCountNewPlay(previousPosition, resolvedPosition)) {
                int existingCount = record.getPlayCount() == null || record.getPlayCount() < 1
                        ? 1
                        : record.getPlayCount();
                record.setPlayCount(existingCount + 1);
            } else if (record.getPlayCount() == null || record.getPlayCount() < 1) {
                record.setPlayCount(1);
            }
            playbackRepository.save(record);
        } else {
            SysUser user = userRepository.findById(userId).orElseThrow();
            UserPlaybackHistory record = UserPlaybackHistory.builder()
                    .user(user)
                    .mediaItem(item)
                    .playedAt(now)
                    .position(resolvedPosition)
                    .durationSeconds(resolvedDuration)
                    .completed(completedValue)
                    .completedAt(completedValue ? now : null)
                    .playCount(1)
                    .build();
            playbackRepository.save(record);
        }
    }

    private boolean shouldCountNewPlay(Integer previousPosition, Integer currentPosition) {
        return currentPosition != null
                && currentPosition <= 10
                && previousPosition != null
                && previousPosition >= 60;
    }

    private Integer resolveDurationSeconds(MediaItem item, Integer durationSeconds) {
        if (durationSeconds != null && durationSeconds > 0) {
            return durationSeconds;
        }
        return fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .map(MediaFile::getDurationSeconds)
                .filter(v -> v != null && v > 0)
                .findFirst()
                .orElse(null);
    }

    private boolean resolveCompleted(Boolean explicitCompleted, Integer position, Integer durationSeconds) {
        if (explicitCompleted != null) {
            return explicitCompleted;
        }
        return position != null
                && durationSeconds != null
                && durationSeconds > 0
                && position >= Math.max(60, Math.floor(durationSeconds * 0.9));
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

    @Transactional
    public boolean toggleWatchlist(Integer userId, Integer mediaItemId) {
        MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
        libraryAccessService.assertCanViewItem(item);

        var existing = watchlistRepository.findByUserIdAndMediaItemId(userId, mediaItemId);
        if (existing.isPresent()) {
            watchlistRepository.delete(existing.get());
            return false;
        }
        SysUser user = userRepository.findById(userId).orElseThrow();
        UserWatchlist watchlist = UserWatchlist.builder()
                .user(user)
                .mediaItem(item)
                .build();
        watchlistRepository.save(watchlist);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isWatchlisted(Integer userId, Integer mediaItemId) {
        return watchlistRepository.existsByUserIdAndMediaItemId(userId, mediaItemId);
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
                    MediaItemResponse resp = toResponse(rec.getMediaItem(), userId, rec);
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> getContinueWatching(Integer userId, int limit) {
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(
                Optional.of(userRepository.findById(userId).orElseThrow()));
        List<UserPlaybackHistory> records = playbackRepository
                .findByUserIdOrderByPlayedAtDesc(userId, PageRequest.of(0, Math.max(limit * 3, limit)));
        List<MediaItemResponse> result = new ArrayList<>();
        for (UserPlaybackHistory record : records) {
            if (result.size() >= limit) {
                break;
            }
            if (!isVisible(record.getMediaItem(), viewableLibraryIds)) {
                continue;
            }
            if (Boolean.TRUE.equals(record.getCompleted())) {
                continue;
            }
            if (record.getPosition() == null || record.getPosition() < 30) {
                continue;
            }
            result.add(toResponse(record.getMediaItem(), userId, record));
        }
        return result;
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
                .map(item -> toResponse(item, userId, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> getWatchlist(Integer userId, int limit) {
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(
                Optional.of(userRepository.findById(userId).orElseThrow()));
        List<UserWatchlist> records = watchlistRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
        return records.stream()
                .map(UserWatchlist::getMediaItem)
                .filter(item -> isVisible(item, viewableLibraryIds))
                .map(item -> toResponse(item, userId, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean isWatched(Integer userId, Integer mediaItemId) {
        return playbackRepository.findByUserIdAndMediaItemId(userId, mediaItemId)
                .map(UserPlaybackHistory::getCompleted)
                .orElse(false);
    }

    @Transactional
    public boolean setWatched(Integer userId, Integer mediaItemId, boolean watched) {
        MediaItem item = mediaItemRepository.findById(mediaItemId).orElseThrow();
        libraryAccessService.assertCanViewItem(item);
        Integer duration = resolveDurationSeconds(item, null);
        Integer position = watched && duration != null ? duration : 0;
        recordPlayback(userId, mediaItemId, position, duration, watched);
        return watched;
    }

    @Transactional(readOnly = true)
    public PlaybackStatsResponse getPlaybackStats(Integer userId, int limit) {
        int cap = Math.min(Math.max(limit, 1), 50);
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(
                Optional.of(userRepository.findById(userId).orElseThrow()));

        List<MediaItemResponse> mostPlayed = playbackRepository
                .findTopPlayedByUserId(userId, PageRequest.of(0, cap * 3))
                .stream()
                .filter(record -> isVisible(record.getMediaItem(), viewableLibraryIds))
                .limit(cap)
                .map(record -> toResponse(record.getMediaItem(), userId, record))
                .collect(Collectors.toList());

        return PlaybackStatsResponse.builder()
                .playedItemCount(playbackRepository.countByUserId(userId))
                .completedItemCount(playbackRepository.countCompletedByUserId(userId))
                .totalPlaybackSeconds(playbackRepository.sumPlaybackSecondsByUserId(userId))
                .favoriteCount(favoriteRepository.countByUserId(userId))
                .watchlistCount(watchlistRepository.countByUserId(userId))
                .recentPlayed(getRecentPlayed(userId, cap))
                .mostPlayed(mostPlayed)
                .watchlist(getWatchlist(userId, cap))
                .build();
    }

    private boolean isVisible(MediaItem item, Set<Integer> viewableLibraryIds) {
        return item != null
                && !Boolean.TRUE.equals(item.getHidden())
                && item.getLibrary() != null
                && viewableLibraryIds.contains(item.getLibrary().getId());
    }

    private MediaItemResponse toResponse(MediaItem item, Integer userId, UserPlaybackHistory playback) {
        Float rating = item.getRating() != null ? item.getRating().floatValue() : null;

        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        List<Integer> fileIds = files.stream()
                .map(MediaFile::getId)
                .collect(Collectors.toList());
        Integer fallbackDuration = files.stream()
                .map(MediaFile::getDurationSeconds)
                .filter(v -> v != null && v > 0)
                .findFirst()
                .orElse(null);
        UserPlaybackHistory history = playback != null
                ? playback
                : playbackRepository.findByUserIdAndMediaItemId(userId, item.getId()).orElse(null);
        Integer duration = history != null && history.getDurationSeconds() != null && history.getDurationSeconds() > 0
                ? history.getDurationSeconds()
                : fallbackDuration;
        Integer position = history != null ? history.getPosition() : null;
        Double percent = position != null && duration != null && duration > 0
                ? Math.round(Math.max(0, Math.min(1, position / (double) duration)) * 1000.0) / 10.0
                : null;

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
                .tags(toTagDtos(item))
                .categories(toCategoryDtos(item))
                .playbackPosition(position)
                .playbackDuration(duration)
                .playbackPercent(percent)
                .watched(history != null && Boolean.TRUE.equals(history.getCompleted()))
                .favorited(favoriteRepository.existsByUserIdAndMediaItemId(userId, item.getId()))
                .watchlisted(watchlistRepository.existsByUserIdAndMediaItemId(userId, item.getId()))
                .build();
    }

    private List<TagDto> toTagDtos(MediaItem item) {
        if (item.getTags() == null) {
            return List.of();
        }
        return item.getTags().stream()
                .map(tag -> TagDto.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .color(tag.getColor())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CategoryDto> toCategoryDtos(MediaItem item) {
        if (item.getCategories() == null) {
            return List.of();
        }
        return item.getCategories().stream()
                .map(category -> CategoryDto.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .type(category.getType())
                        .build())
                .collect(Collectors.toList());
    }
}
