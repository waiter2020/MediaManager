package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.DiscoveryScope;
import com.mediamanager.classification.service.MergeAggressiveness;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagMergeDiscoveryService;
import com.mediamanager.classification.service.TagMergeSnapshot;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.metadata.entity.AudioMetadata;
import com.mediamanager.metadata.entity.ImageMetadata;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.TvShowMetadata;
import com.mediamanager.metadata.repository.AudioMetadataRepository;
import com.mediamanager.metadata.repository.ImageMetadataRepository;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.TvShowMetadataRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiOrganizationService {

    private static final int UNLIMITED = 0;
    private static final int DEFAULT_MAX_COLLECTIONS = 0;
    private static final int DEFAULT_MIN_COLLECTION_TAG_USAGE = 3;
    private static final int DEFAULT_MIN_TAG_COLLECTION_USAGE = 10;
    private static final int DEFAULT_COLLECTION_ITEM_LIMIT = 0;
    private static final int DEFAULT_LOW_USAGE_THRESHOLD = 1;
    private static final int SQL_FETCH_LIMIT = Integer.MAX_VALUE;
    private static final String DIMENSION_TYPE = "TYPE";
    private static final String DIMENSION_GENRE = "GENRE";
    private static final String DIMENSION_CATEGORY = "CATEGORY";
    private static final String DIMENSION_TAG = "TAG";
    private static final String DIMENSION_PUBLISHER = "PUBLISHER";
    private static final String DIMENSION_NETWORK = "NETWORK";
    private static final String DIMENSION_ACTOR = "ACTOR";
    private static final String DIMENSION_ARTIST = "ARTIST";
    private static final String DIMENSION_ALBUM = "ALBUM";
    private static final String DIMENSION_CAMERA = "CAMERA";

    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MovieMetadataRepository movieMetadataRepository;
    private final TvShowMetadataRepository tvShowMetadataRepository;
    private final AudioMetadataRepository audioMetadataRepository;
    private final ImageMetadataRepository imageMetadataRepository;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final TagMergeDiscoveryService tagMergeDiscoveryService;
    private final LibraryAccessService libraryAccessService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AiOrganizationResponse preview(AiOrganizationRequest request) {
        return buildPreview(normalize(request), false);
    }

    @Transactional(readOnly = true)
    public AiOrganizationResponse previewAfterApply(AiOrganizationRequest request) {
        return buildPreview(normalize(request), true);
    }

    AiOrganizationRequest normalize(AiOrganizationRequest request) {
        AiOrganizationRequest source = request != null ? request : new AiOrganizationRequest();
        AiOrganizationRequest normalized = new AiOrganizationRequest();
        normalized.setLibraryId(source.getLibraryId());
        normalized.setMaxCollections(normalizeOptionalLimit(
                source.getMaxCollections(),
                DEFAULT_MAX_COLLECTIONS));
        normalized.setMinCollectionTagUsage(clamp(
                source.getMinCollectionTagUsage(),
                1,
                1000,
                DEFAULT_MIN_COLLECTION_TAG_USAGE));
        normalized.setMinTagCollectionUsage(clamp(
                source.getMinTagCollectionUsage(),
                1,
                1000,
                DEFAULT_MIN_TAG_COLLECTION_USAGE));
        normalized.setCollectionItemLimit(normalizeOptionalLimit(
                source.getCollectionItemLimit(),
                DEFAULT_COLLECTION_ITEM_LIMIT));
        normalized.setLowUsageThreshold(clamp(
                source.getLowUsageThreshold(),
                0,
                1000,
                DEFAULT_LOW_USAGE_THRESHOLD));
        normalized.setMergeDuplicateTags(defaultBool(source.getMergeDuplicateTags(), true));
        normalized.setDeleteUnusedTags(defaultBool(source.getDeleteUnusedTags(), true));
        normalized.setDeleteLowUsageTags(defaultBool(source.getDeleteLowUsageTags(), true));
        normalized.setProtectManualTags(defaultBool(source.getProtectManualTags(), true));
        normalized.setRecolorTags(defaultBool(source.getRecolorTags(), true));
        normalized.setRecolorManualTags(defaultBool(source.getRecolorManualTags(), false));
        normalized.setCreateSmartCollections(defaultBool(source.getCreateSmartCollections(), true));
        normalized.setMergeAggressiveness(
                MergeAggressiveness.from(source.getMergeAggressiveness()).name().toLowerCase());
        return normalized;
    }

    private AiOrganizationResponse buildPreview(AiOrganizationRequest request, boolean applied) {
        // This grouped query is the expensive part. Reuse its result for all tag
        // sections instead of scanning the same join table several times.
        List<AiOrganizationResponse.TagUsage> globalTags = tagRepository.findGlobalUsageCounts().stream()
                .map(this::toTagUsage)
                .toList();
        List<AiOrganizationResponse.TagUsage> unusedTags = unusedTags(globalTags);
        List<AiOrganizationResponse.TagUsage> cleanupTags =
                Boolean.TRUE.equals(request.getDeleteUnusedTags()) || Boolean.TRUE.equals(request.getDeleteLowUsageTags())
                        ? cleanupTags(request, globalTags)
                        : List.of();
        List<AiOrganizationResponse.DuplicateTagGroup> duplicateGroups =
                Boolean.TRUE.equals(request.getMergeDuplicateTags()) ? duplicateGroups(globalTags) : List.of();
        List<AiOrganizationResponse.SemanticMergeGroup> semanticMergeGroups =
                Boolean.TRUE.equals(request.getMergeDuplicateTags())
                        ? semanticMergeGroups(request, globalTags)
                        : List.of();
        List<AiOrganizationResponse.SmartCollectionCandidate> collectionCandidates =
                Boolean.TRUE.equals(request.getCreateSmartCollections()) ? smartCollectionCandidates(request) : List.of();

        return AiOrganizationResponse.builder()
                .libraryId(request.getLibraryId())
                .applied(applied)
                .unusedTagCount(unusedTags.size())
                .cleanupTagCount(cleanupTags.size())
                .duplicateGroupCount(duplicateGroups.size())
                .semanticMergeGroupCount(semanticMergeGroups.size())
                .smartCollectionCandidateCount(collectionCandidates.size())
                .deletedUnusedTagCount(0)
                .deletedCleanupTagCount(0)
                .mergedTagCount(0)
                .translatedTagCount(0)
                .recoloredTagCount(0)
                .createdCollectionCount(0)
                .unusedTags(unusedTags)
                .cleanupTags(cleanupTags)
                .duplicateTagGroups(duplicateGroups)
                .semanticMergeGroups(semanticMergeGroups)
                .smartCollectionCandidates(collectionCandidates)
                .generatedCollections(List.of())
                .build();
    }

    private List<AiOrganizationResponse.TagUsage> unusedTags(List<AiOrganizationResponse.TagUsage> tags) {
        return tags.stream()
                .filter(tag -> tag.getUsageCount() == null || tag.getUsageCount() == 0)
                .toList();
    }

    private List<AiOrganizationResponse.TagUsage> cleanupTags(
            AiOrganizationRequest request,
            List<AiOrganizationResponse.TagUsage> tags) {
        return tags.stream()
                .map(tag -> {
                    tag.setCleanupReason(cleanupReason(tag, request));
                    return tag;
                })
                .filter(tag -> tag.getCleanupReason() != null && !tag.getCleanupReason().isBlank())
                .sorted(Comparator
                        .comparing((AiOrganizationResponse.TagUsage tag) ->
                                tag.getUsageCount() != null ? tag.getUsageCount() : 0L)
                        .thenComparing(AiOrganizationResponse.TagUsage::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String cleanupReason(AiOrganizationResponse.TagUsage tag, AiOrganizationRequest request) {
        long usageCount = tag.getUsageCount() != null ? tag.getUsageCount() : 0L;
        var qualityIssue = tagQualityService.qualityIssue(tag.getName());
        if (qualityIssue.isPresent()
                && (Boolean.TRUE.equals(request.getDeleteUnusedTags())
                || Boolean.TRUE.equals(request.getDeleteLowUsageTags()))) {
            return qualityIssue.get();
        }
        if (Boolean.TRUE.equals(request.getDeleteUnusedTags()) && usageCount == 0) {
            return "未关联任何媒体";
        }
        if (Boolean.TRUE.equals(request.getDeleteLowUsageTags())) {
            return tagQualityService.cleanupReason(
                            tag.getName(),
                            usageCount,
                            tag.getSource(),
                            request.getLowUsageThreshold(),
                            Boolean.TRUE.equals(request.getProtectManualTags()))
                    .orElse(null);
        }
        return null;
    }

    private List<AiOrganizationResponse.SemanticMergeGroup> semanticMergeGroups(
            AiOrganizationRequest request,
            List<AiOrganizationResponse.TagUsage> tags) {
        Map<Integer, AiOrganizationResponse.TagUsage> byId = new LinkedHashMap<>();
        List<TagMergeSnapshot> snapshots = new ArrayList<>();
        for (AiOrganizationResponse.TagUsage tag : tags) {
            if (tag == null || tag.getId() == null || tag.getName() == null || tag.getName().isBlank()) {
                continue;
            }
            byId.put(tag.getId(), tag);
            snapshots.add(new TagMergeSnapshot(
                    tag.getId(),
                    tag.getName(),
                    tag.getUsageCount(),
                    tag.getSource()));
        }
        MergeAggressiveness aggressiveness = MergeAggressiveness.from(request.getMergeAggressiveness());
        return tagMergeDiscoveryService.discoverGroups(
                        snapshots,
                        aggressiveness,
                        request.getLibraryId(),
                        DiscoveryScope.PREVIEW)
                .stream()
                .filter(group -> !"EXACT".equals(group.source()))
                .map(group -> toSemanticMergeGroup(group, byId))
                .filter(group -> group.getCanonicalTag() != null && group.getDuplicateTags() != null
                        && !group.getDuplicateTags().isEmpty())
                .sorted(Comparator.comparing(group ->
                        group.getCanonicalTag().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private AiOrganizationResponse.SemanticMergeGroup toSemanticMergeGroup(
            TagMergeDiscoveryService.DiscoveredMergeGroup group,
            Map<Integer, AiOrganizationResponse.TagUsage> byId) {
        AiOrganizationResponse.TagUsage canonical = byId.get(group.canonicalId());
        List<AiOrganizationResponse.TagUsage> duplicates = group.duplicateIds().stream()
                .map(byId::get)
                .filter(tag -> tag != null)
                .toList();
        return AiOrganizationResponse.SemanticMergeGroup.builder()
                .source(group.source())
                .confidence(group.confidence())
                .reason(group.reason())
                .canonicalTag(canonical)
                .duplicateTags(duplicates)
                .build();
    }

    private List<AiOrganizationResponse.DuplicateTagGroup> duplicateGroups(
            List<AiOrganizationResponse.TagUsage> tags) {
        Map<String, List<AiOrganizationResponse.TagUsage>> byKey = tags.stream()
                .filter(tag -> tag.getName() != null && !tag.getName().isBlank())
                .collect(LinkedHashMap::new, (map, tag) -> {
                    String key = tagCanonicalizationService.semanticKey(tag.getName());
                    if (!key.isBlank()) {
                        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tag);
                    }
                }, Map::putAll);

        return byKey.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> {
                    List<AiOrganizationResponse.TagUsage> groupTags = entry.getValue().stream()
                            .sorted(canonicalSort())
                            .toList();
                    AiOrganizationResponse.TagUsage canonical = groupTags.getFirst();
                    return AiOrganizationResponse.DuplicateTagGroup.builder()
                            .semanticKey(entry.getKey())
                            .canonicalTag(canonical)
                            .duplicateTags(groupTags.stream()
                                    .filter(tag -> !tag.getId().equals(canonical.getId()))
                                    .toList())
                            .build();
                })
                .sorted(Comparator.comparing(group ->
                        group.getCanonicalTag().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Comparator<AiOrganizationResponse.TagUsage> canonicalSort() {
        return Comparator
                .comparing((AiOrganizationResponse.TagUsage tag) ->
                        !tagCanonicalizationService.isPreferredChineseName(tag.getName()))
                .thenComparing(tag -> !"MANUAL".equalsIgnoreCase(safe(tag.getSource())))
                .thenComparing((AiOrganizationResponse.TagUsage tag) ->
                        -(tag.getUsageCount() != null ? tag.getUsageCount() : 0))
                .thenComparing(tag -> tag.getId() != null ? tag.getId() : Integer.MAX_VALUE);
    }

    private List<AiOrganizationResponse.SmartCollectionCandidate> smartCollectionCandidates(
            AiOrganizationRequest request) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(request.getLibraryId());
        if (libraryIds.isEmpty()) {
            return List.of();
        }

        List<AiOrganizationResponse.TagUsage> globalTags = tagRepository.findGlobalUsageCounts().stream()
                .map(this::toTagUsage)
                .toList();
        Set<Integer> excludedTagIds = excludedTagIds(request, globalTags);

        int perDimensionLimit = request.getMaxCollections() != null && request.getMaxCollections() > 0
                ? request.getMaxCollections()
                : SQL_FETCH_LIMIT;

        List<AiOrganizationResponse.SmartCollectionCandidate> candidates = new ArrayList<>();
        candidates.addAll(typeCollectionCandidates(libraryIds, request, perDimensionLimit));
        candidates.addAll(categoryCollectionCandidates(libraryIds, request, perDimensionLimit));
        Map<String, List<AiOrganizationResponse.SmartCollectionCandidate>> metadataGroups =
                metadataCollectionCandidateGroups(libraryIds, request, perDimensionLimit);
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_GENRE, List.of()));
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_PUBLISHER, List.of()));
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_NETWORK, List.of()));
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_ACTOR, List.of()));
        candidates.addAll(tagCollectionCandidates(libraryIds, request, perDimensionLimit, excludedTagIds));
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_ARTIST, List.of()));
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_ALBUM, List.of()));
        candidates.addAll(metadataGroups.getOrDefault(DIMENSION_CAMERA, List.of()));

        return rankCandidates(candidates, request.getMaxCollections());
    }

    private Set<Integer> excludedTagIds(
            AiOrganizationRequest request,
            List<AiOrganizationResponse.TagUsage> globalTags) {
        Set<Integer> excluded = new LinkedHashSet<>();
        cleanupTags(request, globalTags).stream()
                .map(AiOrganizationResponse.TagUsage::getId)
                .filter(id -> id != null)
                .forEach(excluded::add);
        if (Boolean.TRUE.equals(request.getMergeDuplicateTags())) {
            duplicateGroups(globalTags).forEach(group -> group.getDuplicateTags().stream()
                    .map(AiOrganizationResponse.TagUsage::getId)
                    .filter(id -> id != null)
                    .forEach(excluded::add));
        }
        return excluded;
    }

    private List<AiOrganizationResponse.SmartCollectionCandidate> typeCollectionCandidates(
            Set<Integer> libraryIds,
            AiOrganizationRequest request,
            int limit) {
        return mediaItemRepository.findVisibleTypeUsageCounts(
                        libraryIds,
                        request.getMinCollectionTagUsage(),
                        limit)
                .stream()
                .map(row -> {
                    String type = safe(row.getMediaType()).toUpperCase(Locale.ROOT);
                    String display = mediaTypeLabel(type);
                    return AiOrganizationResponse.SmartCollectionCandidate.builder()
                            .key("type:" + type)
                            .dimension(DIMENSION_TYPE)
                            .dimensionLabel(dimensionLabel(DIMENSION_TYPE))
                            .name(collectionName(DIMENSION_TYPE, display))
                            .value(type)
                            .displayValue(display)
                            .usageCount(row.getUsageCount() != null ? row.getUsageCount() : 0L)
                            .build();
                })
                .toList();
    }

    private List<AiOrganizationResponse.SmartCollectionCandidate> categoryCollectionCandidates(
            Set<Integer> libraryIds,
            AiOrganizationRequest request,
            int limit) {
        return categoryRepository.findUsageCountsForLibraries(
                        libraryIds,
                        "GENRE",
                        request.getMinCollectionTagUsage(),
                        limit)
                .stream()
                .map(row -> {
                    String name = safe(row.getCategoryName());
                    return AiOrganizationResponse.SmartCollectionCandidate.builder()
                            .key("category:" + row.getCategoryId())
                            .dimension(DIMENSION_CATEGORY)
                            .dimensionLabel(dimensionLabel(DIMENSION_CATEGORY))
                            .name(collectionName(DIMENSION_CATEGORY, name))
                            .value(name)
                            .displayValue(name)
                            .usageCount(row.getUsageCount() != null ? row.getUsageCount() : 0L)
                            .categoryId(row.getCategoryId())
                            .categoryName(name)
                            .source(row.getCategoryType())
                            .build();
                })
                .filter(candidate -> !candidate.getDisplayValue().isBlank())
                .filter(candidate -> tagQualityService.qualityIssue(candidate.getDisplayValue()).isEmpty())
                .toList();
    }

    private List<AiOrganizationResponse.SmartCollectionCandidate> tagCollectionCandidates(
            Set<Integer> libraryIds,
            AiOrganizationRequest request,
            int limit,
            Set<Integer> excludedTagIds) {
        Map<String, AiOrganizationResponse.SmartCollectionCandidate> bySemanticKey = new LinkedHashMap<>();
        tagRepository.findTopUsageCountsForLibraries(
                        libraryIds,
                        request.getMinTagCollectionUsage(),
                        limit)
                .stream()
                .filter(row -> row.getTagId() != null && !excludedTagIds.contains(row.getTagId()))
                .forEach(row -> {
                    String name = safe(row.getTagName());
                    if (name.isBlank() || tagQualityService.qualityIssue(name).isPresent()) {
                        return;
                    }
                    String semanticKey = tagCanonicalizationService.semanticKey(name);
                    if (semanticKey.isBlank()) {
                        return;
                    }
                    long usageCount = row.getUsageCount() != null ? row.getUsageCount() : 0L;
                    AiOrganizationResponse.SmartCollectionCandidate candidate =
                            AiOrganizationResponse.SmartCollectionCandidate.builder()
                                    .key("tag:" + row.getTagId())
                                    .dimension(DIMENSION_TAG)
                                    .dimensionLabel(dimensionLabel(DIMENSION_TAG))
                                    .name(collectionName(DIMENSION_TAG, name))
                                    .value(name)
                                    .displayValue(name)
                                    .color(row.getColor())
                                    .source(row.getSource())
                                    .usageCount(usageCount)
                                    .tagId(row.getTagId())
                                    .tagName(name)
                                    .build();
                    AiOrganizationResponse.SmartCollectionCandidate existing = bySemanticKey.get(semanticKey);
                    if (existing == null || usageCount > existing.getUsageCount()) {
                        bySemanticKey.put(semanticKey, candidate);
                    }
                });
        return new ArrayList<>(bySemanticKey.values());
    }

    private Map<String, List<AiOrganizationResponse.SmartCollectionCandidate>> metadataCollectionCandidateGroups(
            Set<Integer> libraryIds,
            AiOrganizationRequest request,
            int limit) {
        Map<String, CandidateAccumulator> genres = new LinkedHashMap<>();
        Map<String, CandidateAccumulator> publishers = new LinkedHashMap<>();
        Map<String, CandidateAccumulator> networks = new LinkedHashMap<>();
        Map<String, CandidateAccumulator> actors = new LinkedHashMap<>();
        Map<String, CandidateAccumulator> artists = new LinkedHashMap<>();
        Map<String, CandidateAccumulator> albums = new LinkedHashMap<>();
        Map<String, CandidateAccumulator> cameras = new LinkedHashMap<>();

        for (MovieMetadata metadata : movieMetadataRepository.findVisibleByLibraryIds(libraryIds)) {
            Integer itemId = metadata.getMediaItem() != null ? metadata.getMediaItem().getId() : null;
            addValues(genres, DIMENSION_GENRE, "genre", itemId, readStringArray(metadata.getGenres()));
            addValues(publishers, DIMENSION_PUBLISHER, "studio", itemId, readStringArray(metadata.getStudios()));
            addValues(actors, DIMENSION_ACTOR, "actor", itemId, readCastNames(metadata.getCastInfo()));
        }
        for (TvShowMetadata metadata : tvShowMetadataRepository.findVisibleByLibraryIds(libraryIds)) {
            Integer itemId = metadata.getMediaItem() != null ? metadata.getMediaItem().getId() : null;
            addValues(genres, DIMENSION_GENRE, "genre", itemId, readStringArray(metadata.getGenres()));
            addValue(networks, DIMENSION_NETWORK, "network", itemId, metadata.getNetwork());
            addValues(actors, DIMENSION_ACTOR, "actor", itemId, readCastNames(metadata.getCastInfo()));
        }
        for (AudioMetadata metadata : audioMetadataRepository.findVisibleByLibraryIds(libraryIds)) {
            Integer itemId = metadata.getMediaItem() != null ? metadata.getMediaItem().getId() : null;
            addValues(genres, DIMENSION_GENRE, "genre", itemId, readStringArray(metadata.getGenres()));
            addValue(artists, DIMENSION_ARTIST, "artist", itemId, metadata.getArtist());
            addValue(artists, DIMENSION_ARTIST, "artist", itemId, metadata.getAlbumArtist());
            addValue(albums, DIMENSION_ALBUM, "album", itemId, metadata.getAlbum());
        }
        for (ImageMetadata metadata : imageMetadataRepository.findVisibleByLibraryIds(libraryIds)) {
            Integer itemId = metadata.getMediaItem() != null ? metadata.getMediaItem().getId() : null;
            addValue(cameras, DIMENSION_CAMERA, "camera", itemId, metadata.getCameraMake());
            addValue(cameras, DIMENSION_CAMERA, "camera", itemId, metadata.getCameraModel());
        }

        Map<String, List<AiOrganizationResponse.SmartCollectionCandidate>> groups = new HashMap<>();
        groups.put(DIMENSION_GENRE, toMetadataCandidates(genres, request, limit));
        groups.put(DIMENSION_PUBLISHER, toMetadataCandidates(publishers, request, limit));
        groups.put(DIMENSION_NETWORK, toMetadataCandidates(networks, request, limit));
        groups.put(DIMENSION_ACTOR, toMetadataCandidates(actors, request, limit));
        groups.put(DIMENSION_ARTIST, toMetadataCandidates(artists, request, limit));
        groups.put(DIMENSION_ALBUM, toMetadataCandidates(albums, request, limit));
        groups.put(DIMENSION_CAMERA, toMetadataCandidates(cameras, request, limit));
        return groups;
    }

    private List<AiOrganizationResponse.SmartCollectionCandidate> toMetadataCandidates(
            Map<String, CandidateAccumulator> candidates,
            AiOrganizationRequest request,
            int limit) {
        return candidates.values().stream()
                .filter(candidate -> candidate.usageCount() >= request.getMinCollectionTagUsage())
                .filter(candidate -> tagQualityService.qualityIssue(candidate.displayValue).isEmpty())
                .sorted(Comparator
                        .comparingLong(CandidateAccumulator::usageCount)
                        .reversed()
                        .thenComparing(CandidateAccumulator::displayValue, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(candidate -> AiOrganizationResponse.SmartCollectionCandidate.builder()
                        .key(candidate.dimension + ":" + candidate.metadataField + ":" + candidate.normalizedValue)
                        .dimension(candidate.dimension)
                        .dimensionLabel(dimensionLabel(candidate.dimension))
                        .name(collectionName(candidate.dimension, candidate.displayValue))
                        .value(candidate.value)
                        .displayValue(candidate.displayValue)
                        .usageCount(candidate.usageCount())
                        .metadataField(candidate.metadataField)
                        .metadataValue(candidate.value)
                        .build())
                .toList();
    }

    private List<AiOrganizationResponse.SmartCollectionCandidate> rankCandidates(
            List<AiOrganizationResponse.SmartCollectionCandidate> candidates,
            Integer maxCollections) {
        Set<String> keys = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        List<AiOrganizationResponse.SmartCollectionCandidate> ranked = candidates.stream()
                .filter(candidate -> {
                    String key = safe(candidate.getKey()).toLowerCase(Locale.ROOT);
                    String name = safe(candidate.getName()).toLowerCase(Locale.ROOT);
                    return keys.add(key) && names.add(name);
                })
                .sorted(Comparator
                        .comparingDouble((AiOrganizationResponse.SmartCollectionCandidate candidate) ->
                                candidateScore(candidate))
                        .reversed()
                        .thenComparingInt(candidate -> dimensionPriority(safe(candidate.getDimension())))
                        .thenComparing(candidate -> safe(candidate.getDisplayValue()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (maxCollections == null || maxCollections <= UNLIMITED) {
            return ranked;
        }
        return ranked.stream().limit(maxCollections).toList();
    }

    private double candidateScore(AiOrganizationResponse.SmartCollectionCandidate candidate) {
        long usage = candidate.getUsageCount() != null ? candidate.getUsageCount() : 0L;
        return usage * dimensionWeight(safe(candidate.getDimension()));
    }

    private double dimensionWeight(String dimension) {
        return switch (dimension) {
            case DIMENSION_GENRE, DIMENSION_CATEGORY, DIMENSION_ACTOR,
                 DIMENSION_PUBLISHER, DIMENSION_NETWORK -> 1.0;
            case DIMENSION_TYPE, DIMENSION_ARTIST, DIMENSION_ALBUM, DIMENSION_CAMERA -> 0.8;
            case DIMENSION_TAG -> 0.3;
            default -> 0.5;
        };
    }

    private int dimensionPriority(String dimension) {
        return switch (dimension) {
            case DIMENSION_GENRE, DIMENSION_CATEGORY -> 1;
            case DIMENSION_ACTOR, DIMENSION_PUBLISHER, DIMENSION_NETWORK -> 2;
            case DIMENSION_TYPE -> 3;
            case DIMENSION_ARTIST, DIMENSION_ALBUM, DIMENSION_CAMERA -> 4;
            case DIMENSION_TAG -> 5;
            default -> 6;
        };
    }

    private void addValues(
            Map<String, CandidateAccumulator> candidates,
            String dimension,
            String metadataField,
            Integer itemId,
            List<String> values) {
        new LinkedHashSet<>(values).forEach(value -> addValue(candidates, dimension, metadataField, itemId, value));
    }

    private void addValue(
            Map<String, CandidateAccumulator> candidates,
            String dimension,
            String metadataField,
            Integer itemId,
            String value) {
        if (itemId == null) {
            return;
        }
        String display = normalizeMetadataValue(value);
        if (display.isBlank()) {
            return;
        }
        String normalized = display.toLowerCase(Locale.ROOT);
        CandidateAccumulator candidate = candidates.computeIfAbsent(
                normalized,
                ignored -> new CandidateAccumulator(dimension, metadataField, display, normalized));
        candidate.itemIds.add(itemId);
    }

    private List<String> readStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                List<String> values = new ArrayList<>();
                root.forEach(node -> values.add(node.asText("")));
                return values;
            }
            if (root.isTextual()) {
                return List.of(root.asText());
            }
        } catch (Exception ignored) {
            // Fall back to comma-separated legacy values below.
        }
        return List.of(json.split(","));
    }

    private List<String> readCastNames(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.isObject()) {
                    values.add(node.path("name").asText(""));
                } else if (node.isTextual()) {
                    values.add(node.asText());
                }
            }
            return values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String normalizeMetadataValue(String value) {
        String normalized = safe(value).trim();
        if (normalized.length() > 256) {
            return "";
        }
        return normalized;
    }

    private String collectionName(String dimension, String displayValue) {
        String display = safe(displayValue).trim();
        String name = DIMENSION_TAG.equals(dimension)
                ? "AI - " + display
                : "AI - " + dimensionLabel(dimension) + " - " + display;
        if (name.length() <= 128) {
            return name;
        }
        return name.substring(0, 125) + "...";
    }

    private String dimensionLabel(String dimension) {
        return switch (dimension) {
            case DIMENSION_TYPE -> "\u5a92\u4f53\u7c7b\u578b";
            case DIMENSION_GENRE, DIMENSION_CATEGORY -> "\u7c7b\u578b";
            case DIMENSION_TAG -> "\u6807\u7b7e";
            case DIMENSION_PUBLISHER -> "\u51fa\u7248\u5546";
            case DIMENSION_NETWORK -> "\u7535\u89c6\u7f51";
            case DIMENSION_ACTOR -> "\u6f14\u5458";
            case DIMENSION_ARTIST -> "\u827a\u672f\u5bb6";
            case DIMENSION_ALBUM -> "\u4e13\u8f91";
            case DIMENSION_CAMERA -> "\u76f8\u673a";
            default -> "\u89c4\u5219";
        };
    }

    private String mediaTypeLabel(String type) {
        return switch (safe(type).toUpperCase(Locale.ROOT)) {
            case "MOVIE" -> "\u7535\u5f71";
            case "TV_SHOW" -> "\u5267\u96c6";
            case "EPISODE" -> "\u5355\u96c6";
            case "IMAGE" -> "\u56fe\u7247";
            case "AUDIO" -> "\u97f3\u9891";
            default -> safe(type);
        };
    }

    private boolean defaultBool(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    private int normalizeOptionalLimit(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value <= UNLIMITED) {
            return UNLIMITED;
        }
        return value;
    }

    private AiOrganizationResponse.TagUsage toTagUsage(TagRepository.TagUsageProjection row) {
        return AiOrganizationResponse.TagUsage.builder()
                .id(row.getTagId())
                .name(row.getTagName())
                .color(row.getColor())
                .source(row.getSource())
                .usageCount(row.getUsageCount() != null ? row.getUsageCount() : 0L)
                .build();
    }

    private static final class CandidateAccumulator {
        private final String dimension;
        private final String metadataField;
        private final String value;
        private final String displayValue;
        private final String normalizedValue;
        private final Set<Integer> itemIds = new LinkedHashSet<>();

        private CandidateAccumulator(
                String dimension,
                String metadataField,
                String value,
                String normalizedValue) {
            this.dimension = dimension;
            this.metadataField = metadataField;
            this.value = value;
            this.displayValue = value;
            this.normalizedValue = normalizedValue;
        }

        private long usageCount() {
            return itemIds.size();
        }

        private String displayValue() {
            return displayValue;
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
