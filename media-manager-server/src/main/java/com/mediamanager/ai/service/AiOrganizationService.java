package com.mediamanager.ai.service;

import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiOrganizationService {

    private static final int DEFAULT_MAX_COLLECTIONS = 5;
    private static final int DEFAULT_MIN_COLLECTION_TAG_USAGE = 3;
    private static final int DEFAULT_COLLECTION_ITEM_LIMIT = 50;
    private static final int DEFAULT_LOW_USAGE_THRESHOLD = 1;
    private static final int MAX_COLLECTIONS = 20;
    private static final int MAX_COLLECTION_ITEM_LIMIT = 200;

    private final TagRepository tagRepository;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final LibraryAccessService libraryAccessService;

    public AiOrganizationResponse preview(AiOrganizationRequest request) {
        return buildPreview(normalize(request), false);
    }

    public AiOrganizationResponse previewAfterApply(AiOrganizationRequest request) {
        return buildPreview(normalize(request), true);
    }

    AiOrganizationRequest normalize(AiOrganizationRequest request) {
        AiOrganizationRequest source = request != null ? request : new AiOrganizationRequest();
        AiOrganizationRequest normalized = new AiOrganizationRequest();
        normalized.setLibraryId(source.getLibraryId());
        normalized.setMaxCollections(clamp(source.getMaxCollections(), 1, MAX_COLLECTIONS, DEFAULT_MAX_COLLECTIONS));
        normalized.setMinCollectionTagUsage(clamp(
                source.getMinCollectionTagUsage(),
                1,
                1000,
                DEFAULT_MIN_COLLECTION_TAG_USAGE));
        normalized.setCollectionItemLimit(clamp(
                source.getCollectionItemLimit(),
                1,
                MAX_COLLECTION_ITEM_LIMIT,
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
        List<AiOrganizationResponse.TagUsage> collectionCandidates =
                Boolean.TRUE.equals(request.getCreateSmartCollections()) ? smartCollectionCandidates(request) : List.of();

        return AiOrganizationResponse.builder()
                .libraryId(request.getLibraryId())
                .applied(applied)
                .unusedTagCount(unusedTags.size())
                .cleanupTagCount(cleanupTags.size())
                .duplicateGroupCount(duplicateGroups.size())
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

    private List<AiOrganizationResponse.TagUsage> smartCollectionCandidates(AiOrganizationRequest request) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(request.getLibraryId());
        if (libraryIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findTopUsageCountsForLibraries(
                        libraryIds,
                        request.getMinCollectionTagUsage(),
                        request.getMaxCollections())
                .stream()
                .map(this::toTagUsage)
                .filter(tag -> tagQualityService.qualityIssue(tag.getName()).isEmpty())
                .toList();
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

    private AiOrganizationResponse.TagUsage toTagUsage(TagRepository.TagUsageProjection row) {
        return AiOrganizationResponse.TagUsage.builder()
                .id(row.getTagId())
                .name(row.getTagName())
                .color(row.getColor())
                .source(row.getSource())
                .usageCount(row.getUsageCount() != null ? row.getUsageCount() : 0L)
                .build();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
