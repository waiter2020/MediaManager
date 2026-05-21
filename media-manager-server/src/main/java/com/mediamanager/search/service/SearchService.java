package com.mediamanager.search.service;

import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.EmbeddingIndexService;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaItemSpecification;
import com.mediamanager.media.service.MediaItemService;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final MediaItemRepository itemRepository;
    private final MediaItemService mediaItemService;
    private final LibraryAccessService libraryAccessService;
    private final AiOrchestrator aiOrchestrator;
    private final EmbeddingIndexService embeddingIndexService;
    private final FtsIndexService ftsIndexService;

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> keywordSearch(String q, Integer libraryId, int page, int size) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        if (q != null && !q.isBlank()) {
            List<Integer> ftsIds = ftsIndexService.searchItemIds(q, libraryIds, 1000);
            if (!ftsIds.isEmpty()) {
                return pageByOrderedIds(ftsIds, page, size);
            }
        }
        Specification<MediaItem> spec = MediaItemSpecification.filterBy(libraryIds, null, q, null, null);
        var itemPage = itemRepository.findAll(spec, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<MediaItemResponse> items = itemPage.getContent().stream()
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
        return PageResult.of(items, itemPage.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public List<MediaItemResponse> semanticSearch(String query, Integer libraryId, int limit) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        List<Integer> ids = embeddingIndexService.searchSimilar(query, libraryIds, limit);
        return ids.stream()
                .map(id -> itemRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(i -> !Boolean.TRUE.equals(i.getHidden()))
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
    }

    @Transactional
    public int rebuildFtsIndex() {
        return ftsIndexService.rebuildAll();
    }

    private PageResult<MediaItemResponse> pageByOrderedIds(List<Integer> orderedIds, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        if (from >= orderedIds.size()) {
            return PageResult.of(List.of(), orderedIds.size(), page, size);
        }
        int to = Math.min(orderedIds.size(), from + size);
        List<MediaItemResponse> items = orderedIds.subList(from, to).stream()
                .map(id -> itemRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(i -> !Boolean.TRUE.equals(i.getHidden()))
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
        return PageResult.of(items, orderedIds.size(), page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> naturalLanguageSearch(String query, Integer libraryId, int page, int size) {
        Map<String, Object> parsed = aiOrchestrator.parseNaturalLanguage(query)
                .orElse(Map.of("keyword", query));
        String keyword = parsed.get("keyword") != null ? String.valueOf(parsed.get("keyword")) : query;
        String type = parsed.get("type") != null ? String.valueOf(parsed.get("type")) : null;
        if (type == null || type.isBlank()) {
            return keywordSearch(keyword, libraryId, page, size);
        }
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        Specification<MediaItem> spec = MediaItemSpecification.filterBy(libraryIds, type, keyword, null, null);
        var itemPage = itemRepository.findAll(spec, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<MediaItemResponse> items = itemPage.getContent().stream()
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
        return PageResult.of(items, itemPage.getTotalElements(), page, size);
    }
}
