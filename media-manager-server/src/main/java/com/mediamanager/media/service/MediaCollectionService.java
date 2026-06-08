package com.mediamanager.media.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.common.security.SecurityCurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.dto.MediaCollectionCreateRequest;
import com.mediamanager.media.dto.MediaCollectionItemsRequest;
import com.mediamanager.media.dto.MediaCollectionRuleDto;
import com.mediamanager.media.dto.MediaCollectionResponse;
import com.mediamanager.media.dto.MediaCollectionUpdateRequest;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.entity.MediaCollection;
import com.mediamanager.media.entity.MediaCollectionItem;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaCollectionItemRepository;
import com.mediamanager.media.repository.MediaCollectionRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaItemSpecification;
import com.mediamanager.media.repository.UserPlaybackHistoryRepository;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaCollectionService {

    private final MediaCollectionRepository collectionRepository;
    private final MediaCollectionItemRepository collectionItemRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MediaItemService mediaItemService;
    private final SecurityCurrentUser securityCurrentUser;
    private final LibraryAccessService libraryAccessService;
    private final UserPlaybackHistoryRepository playbackHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<MediaCollectionResponse> listCollections() {
        Optional<SysUser> user = securityCurrentUser.getCurrentUser();
        Integer userId = user.map(SysUser::getId).orElse(null);
        return collectionRepository.findVisibleToUser(userId).stream()
                .map(collection -> toResponse(collection, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MediaCollectionResponse getCollection(Integer id) {
        return getCollection(id, true);
    }

    @Transactional(readOnly = true)
    public MediaCollectionResponse getCollection(Integer id, boolean includeItems) {
        MediaCollection collection = requireCollection(id);
        assertCanViewCollection(collection);
        return toResponse(collection, includeItems);
    }

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> getCollectionItems(Integer id, int page, int size) {
        MediaCollection collection = requireCollection(id);
        assertCanViewCollection(collection);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);

        if (Boolean.TRUE.equals(collection.getSmart())) {
            return smartItemsPage(collection, safePage, safeSize);
        }
        return manualItemsPage(collection, safePage, safeSize);
    }

    @Transactional
    public MediaCollectionResponse createCollection(MediaCollectionCreateRequest request) {
        SysUser user = securityCurrentUser.requireCurrentUser();
        boolean smart = Boolean.TRUE.equals(request.getSmart());
        MediaCollection collection = MediaCollection.builder()
                .owner(user)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .type(normalizeType(request.getType()))
                .visibility(normalizeVisibility(request.getVisibility()))
                .smart(smart)
                .ruleJson(smart ? writeRule(request.getRule()) : null)
                .build();
        MediaCollection saved = collectionRepository.save(collection);
        if (!smart) {
            addItemsInternal(saved, request.getItemIds());
            updatePosterFromFirstItem(saved);
        }
        return toResponse(saved, true);
    }

    @Transactional
    public MediaCollectionResponse updateCollection(Integer id, MediaCollectionUpdateRequest request) {
        MediaCollection collection = requireCollection(id);
        assertCanEditCollection(collection);
        if (request.getName() != null && !request.getName().isBlank()) {
            collection.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            collection.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getVisibility() != null) {
            collection.setVisibility(normalizeVisibility(request.getVisibility()));
        }
        if (request.getSmart() != null) {
            collection.setSmart(request.getSmart());
        }
        if (Boolean.TRUE.equals(collection.getSmart())) {
            MediaCollectionRuleDto nextRule = request.getRule() != null ? request.getRule() : readRule(collection);
            collection.setRuleJson(writeRule(nextRule));
        } else {
            collection.setRuleJson(null);
        }
        return toResponse(collectionRepository.save(collection), true);
    }

    @Transactional
    public void deleteCollection(Integer id) {
        MediaCollection collection = requireCollection(id);
        assertCanEditCollection(collection);
        collectionRepository.delete(collection);
    }

    @Transactional
    public MediaCollectionResponse addItems(Integer id, MediaCollectionItemsRequest request) {
        return addItems(id, request, true);
    }

    @Transactional
    public MediaCollectionResponse addItems(Integer id, MediaCollectionItemsRequest request, boolean includeItems) {
        MediaCollection collection = requireCollection(id);
        assertCanEditCollection(collection);
        assertManualCollection(collection);
        addItemsInternal(collection, request.getItemIds());
        updatePosterFromFirstItem(collection);
        return toResponse(collection, includeItems);
    }

    @Transactional
    public MediaCollectionResponse removeItem(Integer id, Integer mediaItemId) {
        return removeItem(id, mediaItemId, true);
    }

    @Transactional
    public MediaCollectionResponse removeItem(Integer id, Integer mediaItemId, boolean includeItems) {
        MediaCollection collection = requireCollection(id);
        assertCanEditCollection(collection);
        assertManualCollection(collection);
        collectionItemRepository.deleteByCollectionIdAndMediaItemId(collection.getId(), mediaItemId);
        updatePosterFromFirstItem(collection);
        return toResponse(collection, includeItems);
    }

    private void addItemsInternal(MediaCollection collection, List<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        Set<Integer> uniqueIds = new LinkedHashSet<>(itemIds);
        int position = Optional.ofNullable(collectionItemRepository.findMaxPosition(collection.getId())).orElse(-1) + 1;
        for (Integer itemId : uniqueIds) {
            if (collectionItemRepository.existsByCollectionIdAndMediaItemId(collection.getId(), itemId)) {
                continue;
            }
            MediaItem item = mediaItemRepository.findById(itemId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
            libraryAccessService.assertCanViewItem(item);
            collectionItemRepository.save(MediaCollectionItem.builder()
                    .collection(collection)
                    .mediaItem(item)
                    .position(position++)
                    .build());
        }
    }

    private MediaCollectionResponse toResponse(MediaCollection collection, boolean includeItems) {
        boolean smart = Boolean.TRUE.equals(collection.getSmart());
        List<MediaItem> visibleItems;
        MediaItem firstItem;
        long itemCount;
        if (!includeItems && !smart) {
            Page<MediaItem> summaryPage = manualItemPage(collection, PageRequest.of(0, 1));
            visibleItems = List.of();
            firstItem = summaryPage.getContent().stream().findFirst().orElse(null);
            itemCount = summaryPage.getTotalElements();
        } else {
            visibleItems = visibleMediaItems(collection);
            firstItem = visibleItems.isEmpty() ? null : visibleItems.get(0);
            itemCount = visibleItems.size();
        }
        List<MediaItemResponse> items = includeItems
                ? visibleItems.stream()
                        .map(mediaItemService::toResponsePublic)
                        .collect(Collectors.toList())
                : null;
        String posterPath = collection.getPosterPath();
        MediaItemResponse coverItem = null;
        if ((posterPath == null || posterPath.isBlank()) && firstItem != null) {
            posterPath = firstItem.getPosterPath();
            coverItem = mediaItemService.toResponsePublic(firstItem);
        } else if (firstItem != null) {
            coverItem = mediaItemService.toResponsePublic(firstItem);
        }

        SysUser owner = collection.getOwner();
        return MediaCollectionResponse.builder()
                .id(collection.getId())
                .ownerUserId(owner != null ? owner.getId() : null)
                .ownerDisplayName(owner != null ? owner.getDisplayName() : null)
                .name(collection.getName())
                .description(collection.getDescription())
                .type(collection.getType())
                .visibility(collection.getVisibility())
                .posterPath(posterPath)
                .smart(smart)
                .rule(smart ? readRule(collection) : null)
                .coverItem(coverItem)
                .itemCount((int) Math.min(itemCount, Integer.MAX_VALUE))
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .items(items)
                .build();
    }

    private List<MediaItem> visibleMediaItems(MediaCollection collection) {
        if (Boolean.TRUE.equals(collection.getSmart())) {
            return smartItems(collection);
        }
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser());
        return collectionItemRepository.findByCollectionIdOrderByPositionAscCreatedAtAsc(collection.getId()).stream()
                .map(MediaCollectionItem::getMediaItem)
                .filter(item -> item != null
                        && !Boolean.TRUE.equals(item.getHidden())
                        && item.getLibrary() != null
                        && viewableLibraryIds.contains(item.getLibrary().getId()))
                .collect(Collectors.toList());
    }

    private PageResult<MediaItemResponse> manualItemsPage(MediaCollection collection, int page, int size) {
        Page<MediaItem> itemPage = manualItemPage(collection, PageRequest.of(page - 1, size));
        List<MediaItemResponse> items = itemPage.getContent().stream()
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
        return PageResult.of(items, itemPage.getTotalElements(), page, size);
    }

    private Page<MediaItem> manualItemPage(MediaCollection collection, PageRequest pageRequest) {
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser());
        if (viewableLibraryIds.isEmpty()) {
            return Page.empty(pageRequest);
        }
        return collectionItemRepository.findVisibleMediaItems(collection.getId(), viewableLibraryIds, pageRequest);
    }

    private PageResult<MediaItemResponse> smartItemsPage(MediaCollection collection, int page, int size) {
        MediaCollectionRuleDto rule = readRule(collection);
        if (Boolean.TRUE.equals(rule.getUnwatchedOnly())) {
            List<MediaItem> items = smartItems(collection);
            int fromIndex = Math.min((page - 1) * size, items.size());
            int toIndex = Math.min(fromIndex + size, items.size());
            List<MediaItemResponse> responses = items.subList(fromIndex, toIndex).stream()
                    .map(mediaItemService::toResponsePublic)
                    .collect(Collectors.toList());
            return PageResult.of(responses, items.size(), page, size);
        }

        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser());
        Set<Integer> libraryIds = resolveRuleLibraryIds(rule, viewableLibraryIds);
        if (libraryIds.isEmpty()) {
            return PageResult.of(List.of(), 0, page, size);
        }

        int limit = normalizeLimit(rule.getLimit());
        Specification<MediaItem> spec = MediaItemSpecification.filterBy(
                libraryIds,
                normalizeRuleType(rule.getType()),
                trimToNull(rule.getKeyword()),
                toIdSet(rule.getCategoryIds()),
                toIdSet(rule.getTagIds()),
                rule.getMinYear(),
                rule.getMaxYear(),
                rule.getMinRating(),
                rule.getMetadataField(),
                rule.getMetadataValue());
        Page<MediaItem> itemPage = mediaItemRepository.findAll(
                spec,
                PageRequest.of(page - 1, size, resolveRuleSort(rule.getSortField(), rule.getSortOrder())));
        long total = Math.min(itemPage.getTotalElements(), limit);
        int remaining = Math.max(limit - ((page - 1) * size), 0);
        List<MediaItemResponse> responses = itemPage.getContent().stream()
                .limit(remaining)
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
        return PageResult.of(responses, total, page, size);
    }

    private List<MediaItem> smartItems(MediaCollection collection) {
        MediaCollectionRuleDto rule = readRule(collection);
        Set<Integer> viewableLibraryIds = libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser());
        Set<Integer> libraryIds = resolveRuleLibraryIds(rule, viewableLibraryIds);
        int limit = normalizeLimit(rule.getLimit());
        int pageSize = Boolean.TRUE.equals(rule.getUnwatchedOnly()) ? Math.min(limit * 5, 500) : limit;

        Specification<MediaItem> spec = MediaItemSpecification.filterBy(
                libraryIds,
                normalizeRuleType(rule.getType()),
                trimToNull(rule.getKeyword()),
                toIdSet(rule.getCategoryIds()),
                toIdSet(rule.getTagIds()),
                rule.getMinYear(),
                rule.getMaxYear(),
                rule.getMinRating(),
                rule.getMetadataField(),
                rule.getMetadataValue());

        List<MediaItem> items = mediaItemRepository.findAll(
                        spec,
                        PageRequest.of(0, pageSize, resolveRuleSort(rule.getSortField(), rule.getSortOrder())))
                .getContent();

        if (Boolean.TRUE.equals(rule.getUnwatchedOnly())) {
            Optional<SysUser> user = securityCurrentUser.getCurrentUser();
            if (user.isPresent()) {
                Set<Integer> watchedIds = new LinkedHashSet<>(
                        playbackHistoryRepository.findCompletedMediaItemIdsByUserId(user.get().getId()));
                items = items.stream()
                        .filter(item -> !watchedIds.contains(item.getId()))
                        .limit(limit)
                        .collect(Collectors.toList());
            } else {
                items = List.of();
            }
        }
        return items.stream().limit(limit).collect(Collectors.toList());
    }

    private Set<Integer> resolveRuleLibraryIds(MediaCollectionRuleDto rule, Set<Integer> viewableLibraryIds) {
        if (rule.getLibraryId() == null) {
            return viewableLibraryIds;
        }
        if (!viewableLibraryIds.contains(rule.getLibraryId())) {
            return Collections.emptySet();
        }
        return Set.of(rule.getLibraryId());
    }

    private Set<Integer> toIdSet(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Sort resolveRuleSort(String sortField, String sortOrder) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = switch (sortField == null ? "" : sortField) {
            case "title" -> "title";
            case "releaseDate" -> "releaseDate";
            case "rating" -> "rating";
            case "updatedAt" -> "updatedAt";
            default -> "createdAt";
        };
        return Sort.by(direction, field);
    }

    private MediaCollectionRuleDto readRule(MediaCollection collection) {
        if (collection.getRuleJson() == null || collection.getRuleJson().isBlank()) {
            return normalizeRule(new MediaCollectionRuleDto());
        }
        try {
            return normalizeRule(objectMapper.readValue(collection.getRuleJson(), MediaCollectionRuleDto.class));
        } catch (JsonProcessingException e) {
            return normalizeRule(new MediaCollectionRuleDto());
        }
    }

    private String writeRule(MediaCollectionRuleDto rule) {
        try {
            return objectMapper.writeValueAsString(normalizeRule(rule));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Invalid collection rule");
        }
    }

    private MediaCollectionRuleDto normalizeRule(MediaCollectionRuleDto rule) {
        MediaCollectionRuleDto normalized = rule != null ? rule : new MediaCollectionRuleDto();
        normalized.setType(normalizeRuleType(normalized.getType()));
        normalized.setMetadataField(normalizeMetadataField(normalized.getMetadataField()));
        normalized.setMetadataValue(trimToNull(normalized.getMetadataValue()));
        if (normalized.getMetadataField() == null || normalized.getMetadataValue() == null) {
            normalized.setMetadataField(null);
            normalized.setMetadataValue(null);
        }
        normalized.setSortField(normalizeRuleSortField(normalized.getSortField()));
        normalized.setSortOrder("ASC".equalsIgnoreCase(normalized.getSortOrder()) ? "ASC" : "DESC");
        normalized.setLimit(normalizeLimit(normalized.getLimit()));
        return normalized;
    }

    private String normalizeRuleType(String type) {
        String value = trimToNull(type);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String normalizeMetadataField(String metadataField) {
        String value = trimToNull(metadataField);
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "genre", "genres" -> "genre";
            case "studio", "studios", "publisher" -> "studio";
            case "network" -> "network";
            case "actor", "cast", "performer" -> "actor";
            case "artist" -> "artist";
            case "album" -> "album";
            case "camera" -> "camera";
            default -> null;
        };
    }

    private String normalizeRuleSortField(String sortField) {
        return switch (sortField == null ? "" : sortField) {
            case "title", "releaseDate", "rating", "updatedAt" -> sortField;
            default -> "createdAt";
        };
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 50;
        }
        return Math.min(Math.max(limit, 1), 200);
    }

    private void assertManualCollection(MediaCollection collection) {
        if (Boolean.TRUE.equals(collection.getSmart())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Smart collections are rule-driven");
        }
    }

    private void updatePosterFromFirstItem(MediaCollection collection) {
        if (collection.getPosterPath() != null && !collection.getPosterPath().isBlank()) {
            return;
        }
        collectionItemRepository.findByCollectionIdOrderByPositionAscCreatedAtAsc(collection.getId()).stream()
                .map(MediaCollectionItem::getMediaItem)
                .filter(item -> item.getPosterPath() != null && !item.getPosterPath().isBlank())
                .findFirst()
                .ifPresent(item -> {
                    collection.setPosterPath(item.getPosterPath());
                    collectionRepository.save(collection);
                });
    }

    private MediaCollection requireCollection(Integer id) {
        return collectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAMETER, "Collection not found"));
    }

    private void assertCanViewCollection(MediaCollection collection) {
        if ("SHARED".equalsIgnoreCase(collection.getVisibility())) {
            return;
        }
        assertCanEditCollection(collection);
    }

    private void assertCanEditCollection(MediaCollection collection) {
        SysUser user = securityCurrentUser.requireCurrentUser();
        if (collection.getOwner() == null || !collection.getOwner().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Cannot edit this collection");
        }
    }

    private String normalizeType(String type) {
        if ("PLAYLIST".equalsIgnoreCase(type)) {
            return "PLAYLIST";
        }
        return "COLLECTION";
    }

    private String normalizeVisibility(String visibility) {
        if ("SHARED".equalsIgnoreCase(visibility)) {
            return "SHARED";
        }
        return "PRIVATE";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
