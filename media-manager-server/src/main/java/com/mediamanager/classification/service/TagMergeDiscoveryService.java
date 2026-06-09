package com.mediamanager.classification.service;

import com.mediamanager.ai.service.AiTagSemanticMergeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TagMergeDiscoveryService {

    private static final double AUTO_MERGE_CONFIDENCE = 0.95;
    private static final int MAX_PREVIEW_GROUPS = 50;

    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagSimilarityService tagSimilarityService;
    private final TagEmbeddingClusterService tagEmbeddingClusterService;
    private final AiTagSemanticMergeService tagSemanticMergeService;

    public record DiscoveredMergeGroup(
            Integer canonicalId,
            List<Integer> duplicateIds,
            String source,
            double confidence,
            String reason) {
    }

    public List<DiscoveredMergeGroup> discoverGroups(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness aggressiveness,
            Integer libraryId,
            DiscoveryScope scope) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<DiscoveredMergeGroup> groups = new ArrayList<>();
        Set<Integer> usedIds = new HashSet<>();

        addExactGroups(tags, groups, usedIds);
        addStructureGroups(tags, aggressiveness, groups, usedIds);
        if (scope == DiscoveryScope.APPLY_MERGE) {
            addEmbeddingGroups(tags, libraryId, groups, usedIds);
        }

        return groups.stream()
                .sorted(Comparator
                        .comparing(DiscoveredMergeGroup::confidence).reversed()
                        .thenComparing(DiscoveredMergeGroup::source))
                .limit(MAX_PREVIEW_GROUPS)
                .toList();
    }

    public List<DiscoveredMergeGroup> autoMergeGroups(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness aggressiveness,
            Integer libraryId) {
        return discoverGroups(tags, aggressiveness, libraryId, DiscoveryScope.APPLY_MERGE).stream()
                .filter(group -> group.confidence() >= AUTO_MERGE_CONFIDENCE)
                .toList();
    }

    public List<DiscoveredMergeGroup> discoverAiReviewGroups(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness aggressiveness,
            Integer libraryId) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<DiscoveredMergeGroup> groups = new ArrayList<>();
        addAiGroups(tags, aggressiveness, libraryId, groups, new HashSet<>());
        return groups;
    }

    private void addExactGroups(
            List<TagMergeSnapshot> tags,
            List<DiscoveredMergeGroup> groups,
            Set<Integer> usedIds) {
        Map<String, List<TagMergeSnapshot>> byKey = new LinkedHashMap<>();
        for (TagMergeSnapshot tag : tags) {
            if (tag == null || tag.id() == null || tag.name() == null) {
                continue;
            }
            String key = tagCanonicalizationService.semanticKey(tag.name());
            if (!key.isBlank()) {
                byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tag);
            }
        }
        for (List<TagMergeSnapshot> members : byKey.values()) {
            if (members.size() < 2) {
                continue;
            }
            members.sort(Comparator
                    .comparing((TagMergeSnapshot tag) -> -(tag.usageCount() != null ? tag.usageCount() : 0L))
                    .thenComparing(tag -> tag.id() != null ? tag.id() : Integer.MAX_VALUE));
            TagMergeSnapshot canonical = members.getFirst();
            List<Integer> duplicateIds = members.stream()
                    .skip(1)
                    .map(TagMergeSnapshot::id)
                    .filter(id -> !usedIds.contains(id) && !id.equals(canonical.id()))
                    .toList();
            if (duplicateIds.isEmpty()) {
                continue;
            }
            groups.add(new DiscoveredMergeGroup(
                    canonical.id(), duplicateIds, "EXACT", 1.0, "normalized-key"));
            markUsed(usedIds, canonical.id(), duplicateIds);
        }
    }

    private void addStructureGroups(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness aggressiveness,
            List<DiscoveredMergeGroup> groups,
            Set<Integer> usedIds) {
        for (TagSimilarityService.SimilarTagCluster cluster
                : tagSimilarityService.clusterByStructure(availableTags(tags, usedIds), aggressiveness)) {
            List<Integer> duplicateIds = cluster.memberIds().stream()
                    .filter(id -> !usedIds.contains(id))
                    .toList();
            if (duplicateIds.isEmpty() || usedIds.contains(cluster.canonicalId())) {
                continue;
            }
            groups.add(new DiscoveredMergeGroup(
                    cluster.canonicalId(),
                    duplicateIds,
                    "STRUCTURE",
                    cluster.confidence(),
                    cluster.reason()));
            markUsed(usedIds, cluster.canonicalId(), duplicateIds);
        }
    }

    private void addEmbeddingGroups(
            List<TagMergeSnapshot> tags,
            Integer libraryId,
            List<DiscoveredMergeGroup> groups,
            Set<Integer> usedIds) {
        for (TagEmbeddingClusterService.EmbeddingCluster cluster
                : tagEmbeddingClusterService.clusterByEmbedding(availableTags(tags, usedIds), libraryId)) {
            List<Integer> duplicateIds = cluster.memberIds().stream()
                    .filter(id -> !usedIds.contains(id))
                    .toList();
            if (duplicateIds.isEmpty() || usedIds.contains(cluster.canonicalId())) {
                continue;
            }
            groups.add(new DiscoveredMergeGroup(
                    cluster.canonicalId(),
                    duplicateIds,
                    "EMBEDDING",
                    cluster.confidence(),
                    cluster.reason()));
            markUsed(usedIds, cluster.canonicalId(), duplicateIds);
        }
    }

    private void addAiGroups(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness aggressiveness,
            Integer libraryId,
            List<DiscoveredMergeGroup> groups,
            Set<Integer> usedIds) {
        List<TagMergeSnapshot> remaining = availableTags(tags, usedIds);
        if (remaining.size() < 2) {
            return;
        }
        List<List<AiTagSemanticMergeService.ClusterTag>> clusters =
                tagSemanticMergeService.buildCandidateClusters(remaining, aggressiveness);
        for (AiTagSemanticMergeService.SemanticMergeGroup group
                : tagSemanticMergeService.suggestGroups(libraryId, clusters, aggressiveness)) {
            List<Integer> duplicateIds = group.duplicateIds().stream()
                    .filter(id -> !usedIds.contains(id))
                    .toList();
            if (duplicateIds.isEmpty() || usedIds.contains(group.canonicalId())) {
                continue;
            }
            groups.add(new DiscoveredMergeGroup(
                    group.canonicalId(),
                    duplicateIds,
                    "AI",
                    group.confidence(),
                    group.reason()));
            markUsed(usedIds, group.canonicalId(), duplicateIds);
        }
    }

    private List<TagMergeSnapshot> availableTags(List<TagMergeSnapshot> tags, Set<Integer> usedIds) {
        return tags.stream()
                .filter(tag -> tag != null && tag.id() != null && !usedIds.contains(tag.id()))
                .toList();
    }

    private void markUsed(Set<Integer> usedIds, Integer canonicalId, List<Integer> duplicateIds) {
        usedIds.add(canonicalId);
        usedIds.addAll(duplicateIds);
    }
}
