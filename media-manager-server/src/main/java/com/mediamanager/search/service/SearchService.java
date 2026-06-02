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

import com.mediamanager.search.dto.NlSearchResult;
import com.mediamanager.search.dto.SemanticSearchItem;
import com.mediamanager.search.dto.SemanticSearchResult;
import com.mediamanager.search.dto.UnifiedSearchResult;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int UNIFIED_CANDIDATE_LIMIT = 1000;

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
    public SemanticSearchResult semanticSearch(String query, Integer libraryId, int limit) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        List<EmbeddingIndexService.Scored> scored = embeddingIndexService.searchSimilarWithScores(query, libraryIds, limit);
        
        List<SemanticSearchItem> scoredItems = new ArrayList<>();
        List<MediaItemResponse> items = new ArrayList<>();
        
        for (EmbeddingIndexService.Scored s : scored) {
            MediaItem item = itemRepository.findById(s.itemId()).orElse(null);
            if (item != null && !Boolean.TRUE.equals(item.getHidden())) {
                MediaItemResponse resp = mediaItemService.toResponsePublic(item);
                items.add(resp);
                scoredItems.add(new SemanticSearchItem(resp, s.score()));
            }
        }
        
        return SemanticSearchResult.builder()
                .items(items)
                .scoredItems(scoredItems)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> semanticSearch(String query, Integer libraryId, int page, int size) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        List<EmbeddingIndexService.Scored> scored = embeddingIndexService.searchSimilarWithScores(query, libraryIds, 1000);
        
        List<EmbeddingIndexService.Scored> filtered = scored.stream()
                .filter(s -> s.score() >= 0.6f)
                .collect(Collectors.toList());
                
        int total = filtered.size();
        int from = Math.max(0, (page - 1) * size);
        if (from >= total) {
            return PageResult.of(List.of(), total, page, size);
        }
        int to = Math.min(total, from + size);
        
        List<MediaItemResponse> items = filtered.subList(from, to).stream()
                .map(s -> itemRepository.findById(s.itemId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(i -> !Boolean.TRUE.equals(i.getHidden()))
                .map(mediaItemService::toResponsePublic)
                .collect(Collectors.toList());
                
        return PageResult.of(items, total, page, size);
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
    public UnifiedSearchResult unifiedSearch(
            String query,
            Integer libraryId,
            String type,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            Integer minYear,
            Integer maxYear,
            Double minRating,
            int page,
            int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        String trimmedQuery = query != null ? query.trim() : "";
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);

        Optional<Map<String, Object>> parsedFromAi = Optional.empty();
        if (!trimmedQuery.isBlank()) {
            try {
                parsedFromAi = aiOrchestrator.parseNaturalLanguage(trimmedQuery);
            } catch (Exception ignored) {
                parsedFromAi = Optional.empty();
            }
        }
        Map<String, Object> parsedFilters = parsedFromAi
                .map(parsed -> new HashMap<String, Object>(parsed))
                .orElseGet(() -> {
                    HashMap<String, Object> fallback = new HashMap<>();
                    if (trimmedQuery.isBlank()) {
                        return fallback;
                    }
                    fallback.put("keyword", trimmedQuery);
                    return fallback;
                });

        String parsedKeyword = stringValue(parsedFilters.get("keyword"));
        String keyword = hasText(parsedKeyword) ? parsedKeyword : trimmedQuery;
        String effectiveType = normalizeType(hasText(type) ? type : stringValue(parsedFilters.get("type")));
        Integer effectiveMinYear = minYear != null ? minYear : intValue(parsedFilters.get("minYear"));
        Integer effectiveMaxYear = maxYear != null ? maxYear : intValue(parsedFilters.get("maxYear"));
        Double effectiveMinRating = minRating != null ? minRating : doubleValue(parsedFilters.get("minRating"));

        Map<Integer, RankedCandidate> candidates = new HashMap<>();

        List<Integer> keywordIds = findFilteredCandidateIds(
                keyword,
                libraryIds,
                effectiveType,
                categoryIds,
                tagIds,
                effectiveMinYear,
                effectiveMaxYear,
                effectiveMinRating,
                UNIFIED_CANDIDATE_LIMIT);
        String primarySource = parsedFromAi.isPresent() ? "naturalLanguage" : "keyword";
        for (int i = 0; i < keywordIds.size(); i++) {
            addCandidate(candidates, keywordIds.get(i), 10000d - i, primarySource);
        }

        if (!trimmedQuery.isBlank()) {
            try {
                List<EmbeddingIndexService.Scored> semantic = embeddingIndexService.searchSimilarWithScores(
                        trimmedQuery,
                        libraryIds,
                        UNIFIED_CANDIDATE_LIMIT);
                for (EmbeddingIndexService.Scored scored : semantic) {
                    if (scored.score() < 0.6f) {
                        continue;
                    }
                    MediaItem item = itemRepository.findById(scored.itemId()).orElse(null);
                    if (matchesUnifiedFilters(
                            item,
                            libraryIds,
                            effectiveType,
                            categoryIds,
                            tagIds,
                            effectiveMinYear,
                            effectiveMaxYear,
                            effectiveMinRating)) {
                        addCandidate(candidates, scored.itemId(), 5000d + (scored.score() * 1000d), "semantic");
                    }
                }
            } catch (Exception ignored) {
                // Keyword and parsed filter results remain usable when embedding search is unavailable.
            }
        }

        if (candidates.isEmpty() && hasText(trimmedQuery) && !trimmedQuery.equals(keyword)) {
            List<Integer> fallbackIds = findFilteredCandidateIds(
                    trimmedQuery,
                    libraryIds,
                    effectiveType,
                    categoryIds,
                    tagIds,
                    effectiveMinYear,
                    effectiveMaxYear,
                    effectiveMinRating,
                    UNIFIED_CANDIDATE_LIMIT);
            for (int i = 0; i < fallbackIds.size(); i++) {
                addCandidate(candidates, fallbackIds.get(i), 8000d - i, "keyword");
            }
        }

        List<RankedCandidate> ordered = candidates.values().stream()
                .sorted(Comparator.comparingDouble((RankedCandidate c) -> c.score).reversed()
                        .thenComparing(c -> c.itemId))
                .toList();

        int from = Math.max(0, (safePage - 1) * safeSize);
        int to = Math.min(ordered.size(), from + safeSize);
        List<MediaItemResponse> pageItems = from >= ordered.size()
                ? List.of()
                : ordered.subList(from, to).stream()
                        .map(candidate -> itemRepository.findById(candidate.itemId).orElse(null))
                        .filter(Objects::nonNull)
                        .map(mediaItemService::toResponsePublic)
                        .collect(Collectors.toList());

        LinkedHashSet<String> sources = new LinkedHashSet<>();
        ordered.forEach(candidate -> sources.addAll(candidate.sources));

        return UnifiedSearchResult.builder()
                .results(PageResult.of(pageItems, ordered.size(), safePage, safeSize))
                .parsedFilters(parsedFilters)
                .sources(new ArrayList<>(sources))
                .build();
    }

    private List<Integer> findFilteredCandidateIds(
            String keyword,
            Set<Integer> libraryIds,
            String type,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            Integer minYear,
            Integer maxYear,
            Double minRating,
            int limit) {
        String normalizedKeyword = hasText(keyword) ? keyword.trim() : null;
        if (normalizedKeyword != null) {
            List<Integer> ftsIds = ftsIndexService.searchItemIds(normalizedKeyword, libraryIds, limit);
            if (!ftsIds.isEmpty()) {
                List<Integer> filteredIds = ftsIds.stream()
                        .map(id -> itemRepository.findById(id).orElse(null))
                        .filter(item -> matchesUnifiedFilters(
                                item,
                                libraryIds,
                                type,
                                categoryIds,
                                tagIds,
                                minYear,
                                maxYear,
                                minRating))
                        .map(MediaItem::getId)
                        .limit(limit)
                        .collect(Collectors.toList());
                if (!filteredIds.isEmpty()) {
                    return filteredIds;
                }
            }
        }

        Specification<MediaItem> spec = MediaItemSpecification.filterBy(
                libraryIds,
                type,
                normalizedKeyword,
                categoryIds,
                tagIds,
                minYear,
                maxYear,
                minRating);
        return itemRepository.findAll(
                        spec,
                        PageRequest.of(0, Math.max(limit, 1), Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(MediaItem::getId)
                .collect(Collectors.toList());
    }

    private boolean matchesUnifiedFilters(
            MediaItem item,
            Set<Integer> libraryIds,
            String type,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            Integer minYear,
            Integer maxYear,
            Double minRating) {
        if (item == null || Boolean.TRUE.equals(item.getHidden())) {
            return false;
        }
        Integer itemLibraryId = item.getLibrary() != null ? item.getLibrary().getId() : null;
        if (libraryIds != null && (itemLibraryId == null || !libraryIds.contains(itemLibraryId))) {
            return false;
        }
        if (hasText(type) && !type.equals(item.getType())) {
            return false;
        }
        if (minYear != null && (item.getReleaseDate() == null || item.getReleaseDate().getYear() < minYear)) {
            return false;
        }
        if (maxYear != null && (item.getReleaseDate() == null || item.getReleaseDate().getYear() > maxYear)) {
            return false;
        }
        if (minRating != null && (item.getRating() == null || item.getRating().doubleValue() < minRating)) {
            return false;
        }
        if (categoryIds != null && !categoryIds.isEmpty()
                && (item.getCategories() == null
                || item.getCategories().stream().noneMatch(category -> categoryIds.contains(category.getId())))) {
            return false;
        }
        return tagIds == null || tagIds.isEmpty()
                || (item.getTags() != null && item.getTags().stream().anyMatch(tag -> tagIds.contains(tag.getId())));
    }

    private void addCandidate(Map<Integer, RankedCandidate> candidates, Integer itemId, double score, String source) {
        if (itemId == null) {
            return;
        }
        RankedCandidate candidate = candidates.computeIfAbsent(itemId, RankedCandidate::new);
        candidate.score = Math.max(candidate.score, score);
        candidate.sources.add(source);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalizeType(String value) {
        if (!hasText(value)) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "MOVIES", "电影" -> "MOVIE";
            case "TV", "TVSHOW", "TV_SHOWS", "SERIES", "剧集", "电视剧" -> "TV_SHOW";
            case "EPISODES", "单集" -> "EPISODE";
            case "IMAGES", "图片" -> "IMAGE";
            case "AUDIOS", "MUSIC", "音乐", "音频" -> "AUDIO";
            default -> upper;
        };
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value != null ? Double.parseDouble(String.valueOf(value)) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class RankedCandidate {
        private final Integer itemId;
        private double score = Double.NEGATIVE_INFINITY;
        private final LinkedHashSet<String> sources = new LinkedHashSet<>();

        private RankedCandidate(Integer itemId) {
            this.itemId = itemId;
        }
    }

    @Transactional(readOnly = true)
    public NlSearchResult naturalLanguageSearch(String query, Integer libraryId, int page, int size) {
        Map<String, Object> parsed = aiOrchestrator.parseNaturalLanguage(query)
                .orElse(Map.of("keyword", query));
        String keyword = parsed.get("keyword") != null ? String.valueOf(parsed.get("keyword")) : query;
        String type = parsed.get("type") != null ? String.valueOf(parsed.get("type")) : null;
        
        Integer minYear = null;
        Integer maxYear = null;
        Double minRating = null;
        
        try {
            if (parsed.get("minYear") != null) {
                minYear = ((Number) parsed.get("minYear")).intValue();
            }
        } catch (Exception ignored) {}
        try {
            if (parsed.get("maxYear") != null) {
                maxYear = ((Number) parsed.get("maxYear")).intValue();
            }
        } catch (Exception ignored) {}
        try {
            if (parsed.get("minRating") != null) {
                minRating = ((Number) parsed.get("minRating")).doubleValue();
            }
        } catch (Exception ignored) {}
        
        boolean hasStructuredFilter = (type != null && !type.isBlank()) 
                || minYear != null 
                || maxYear != null 
                || minRating != null;
                
        PageResult<MediaItemResponse> results;
        if (!hasStructuredFilter) {
            results = keywordSearch(keyword, libraryId, page, size);
        } else {
            Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
            Specification<MediaItem> spec = MediaItemSpecification.filterBy(
                    libraryIds, type, keyword, null, null, minYear, maxYear, minRating);
            var itemPage = itemRepository.findAll(spec, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
            List<MediaItemResponse> items = itemPage.getContent().stream()
                    .map(mediaItemService::toResponsePublic)
                    .collect(Collectors.toList());
            results = PageResult.of(items, itemPage.getTotalElements(), page, size);
        }
        
        return NlSearchResult.builder()
                .results(results)
                .parsedFilters(parsed)
                .build();
    }

    @Transactional(readOnly = true)
    public SemanticSearchResult similarToItem(Integer itemId, int limit) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(null);
        List<EmbeddingIndexService.Scored> scored = embeddingIndexService.searchSimilarToItem(itemId, libraryIds, limit);
        
        List<SemanticSearchItem> scoredItems = new ArrayList<>();
        List<MediaItemResponse> items = new ArrayList<>();
        
        for (EmbeddingIndexService.Scored s : scored) {
            MediaItem item = itemRepository.findById(s.itemId()).orElse(null);
            if (item != null && !Boolean.TRUE.equals(item.getHidden())) {
                MediaItemResponse resp = mediaItemService.toResponsePublic(item);
                items.add(resp);
                scoredItems.add(new SemanticSearchItem(resp, s.score()));
            }
        }
        
        return SemanticSearchResult.builder()
                .items(items)
                .scoredItems(scoredItems)
                .build();
    }
}
