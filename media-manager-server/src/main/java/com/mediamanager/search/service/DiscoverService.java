package com.mediamanager.search.service;

import com.mediamanager.common.security.SecurityCurrentUser;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaItemSpecification;
import com.mediamanager.media.service.MediaItemService;
import com.mediamanager.media.service.UserActivityService;
import com.mediamanager.search.dto.DiscoverResponse;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiscoverService {

    private final UserActivityService userActivityService;
    private final MediaItemRepository itemRepository;
    private final MediaItemService mediaItemService;
    private final LibraryAccessService libraryAccessService;
    private final SecurityCurrentUser securityCurrentUser;

    @Transactional(readOnly = true)
    public DiscoverResponse discover(int limit) {
        int cap = Math.min(Math.max(limit, 1), 50);
        Optional<SysUser> user = securityCurrentUser.getCurrentUser();
        Set<Integer> libraryIds = libraryAccessService.getViewableLibraryIds(user);

        List<MediaItemResponse> continueWatching = List.of();
        if (user.isPresent()) {
            continueWatching = userActivityService.getRecentPlayed(user.get().getId(), cap);
        }

        Specification<MediaItem> visibleSpec = MediaItemSpecification.filterBy(libraryIds, null, null, null, null);
        List<MediaItem> recentEntities = itemRepository.findAll(
                visibleSpec,
                PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        List<MediaItemResponse> recentlyAdded = recentEntities.stream()
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());

        List<MediaItemResponse> recommended = buildRecommended(libraryIds, continueWatching, recentlyAdded, cap);

        return DiscoverResponse.builder()
                .continueWatching(continueWatching)
                .recommended(recommended)
                .recentlyAdded(recentlyAdded)
                .build();
    }

    private List<MediaItemResponse> buildRecommended(
            Set<Integer> libraryIds,
            List<MediaItemResponse> continueWatching,
            List<MediaItemResponse> recentlyAdded,
            int cap) {
        Set<Integer> exclude = new LinkedHashSet<>();
        continueWatching.forEach(i -> exclude.add(i.getId()));
        recentlyAdded.forEach(i -> exclude.add(i.getId()));

        Specification<MediaItem> spec = MediaItemSpecification.filterBy(libraryIds, null, null, null, null);
        List<MediaItem> rated = itemRepository.findAll(
                spec,
                PageRequest.of(0, cap * 3, Sort.by(Sort.Direction.DESC, "rating"))).getContent();

        List<MediaItemResponse> result = new ArrayList<>();
        for (MediaItem item : rated) {
            if (item.getRating() == null) {
                continue;
            }
            if (exclude.contains(item.getId())) {
                continue;
            }
            result.add(mediaItemService.toResponsePublic(item));
            if (result.size() >= cap) {
                break;
            }
        }
        if (result.isEmpty()) {
            return recentlyAdded.stream().limit(cap).collect(Collectors.toList());
        }
        return result;
    }
}
