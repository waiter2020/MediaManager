package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.service.MergeAggressiveness;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagMergeSnapshot;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.classification.service.TagSimilarityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTagSemanticMergeService {

    private static final int MAX_CLUSTER_SIZE = 20;
    private static final int MAX_AI_CLUSTERS = 30;
    private static final long AI_CLUSTER_PAUSE_MS = 200L;
    private static final double MIN_CONFIDENCE_CONSERVATIVE = 0.86;
    private static final double MIN_CONFIDENCE_STANDARD = 0.82;
    private static final double MIN_CONFIDENCE_AGGRESSIVE = 0.78;

    private final AiOrchestrator aiOrchestrator;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final TagSimilarityService tagSimilarityService;
    private final ObjectMapper objectMapper;

    public record ClusterTag(Integer id, String name) {
        static ClusterTag from(TagMergeSnapshot snapshot) {
            return new ClusterTag(snapshot.id(), snapshot.name());
        }
    }

    public record SemanticMergeGroup(
            Integer canonicalId,
            List<Integer> duplicateIds,
            double confidence,
            String reason) {
    }

    public List<List<ClusterTag>> buildCandidateClusters(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness aggressiveness) {
        List<TagMergeSnapshot> candidates = mergeCandidates(tags);
        if (candidates.size() < 2) {
            return List.of();
        }
        List<List<ClusterTag>> clusters = new ArrayList<>();
        Set<Integer> clustered = new HashSet<>();

        for (TagSimilarityService.SimilarTagCluster cluster
                : tagSimilarityService.clusterByStructure(candidates, aggressiveness)) {
            List<ClusterTag> members = new ArrayList<>();
            members.add(new ClusterTag(cluster.canonicalId(), findName(candidates, cluster.canonicalId())));
            for (Integer duplicateId : cluster.memberIds()) {
                members.add(new ClusterTag(duplicateId, findName(candidates, duplicateId)));
            }
            if (members.size() >= 2 && members.size() <= MAX_CLUSTER_SIZE) {
                clusters.add(members);
                members.forEach(tag -> clustered.add(tag.id()));
            }
        }

        List<ClusterTag> leftovers = candidates.stream()
                .filter(tag -> !clustered.contains(tag.id()))
                .map(ClusterTag::from)
                .toList();
        for (int i = 0; i < leftovers.size(); i += MAX_CLUSTER_SIZE) {
            List<ClusterTag> batch = leftovers.subList(i, Math.min(i + MAX_CLUSTER_SIZE, leftovers.size()));
            if (batch.size() >= 2) {
                clusters.add(batch);
            }
        }
        return clusters;
    }

    public List<SemanticMergeGroup> suggestGroups(
            Integer libraryId,
            List<List<ClusterTag>> clusters,
            MergeAggressiveness aggressiveness) {
        if (clusters == null || clusters.isEmpty()) {
            return List.of();
        }
        AiProvider provider = aiOrchestrator.resolve(libraryId, AiTaskType.COMPLETE_METADATA);
        if ("noop".equalsIgnoreCase(provider.providerId())) {
            return List.of();
        }

        List<SemanticMergeGroup> groups = new ArrayList<>();
        int processedClusters = 0;
        for (List<ClusterTag> cluster : clusters) {
            if (processedClusters >= MAX_AI_CLUSTERS) {
                log.info("Reached AI semantic merge cluster limit ({}) for this apply run", MAX_AI_CLUSTERS);
                break;
            }
            if (cluster == null || cluster.size() < 2) {
                continue;
            }
            try {
                provider.completeMetadata(
                                prompt(cluster, aggressiveness),
                                aiOrchestrator.defaultConfig(libraryId, AiTaskType.COMPLETE_METADATA))
                        .map(raw -> parseGroups(raw, cluster, aggressiveness))
                        .ifPresent(groups::addAll);
            } catch (Exception e) {
                log.warn("Failed to generate AI semantic tag merge suggestions: {}", e.getMessage());
            }
            processedClusters++;
            sleepQuietly(AI_CLUSTER_PAUSE_MS);
        }
        return removeConflicts(groups);
    }

    private List<TagMergeSnapshot> mergeCandidates(List<TagMergeSnapshot> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && tag.id() != null)
                .filter(tag -> {
                    String name = tagCanonicalizationService.normalizeDisplayName(tag.name());
                    return !name.isBlank()
                            && name.length() <= 64
                            && tagQualityService.qualityIssue(name).isEmpty();
                })
                .sorted(Comparator.comparing(tag -> tag.name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String prompt(List<ClusterTag> cluster, MergeAggressiveness aggressiveness) {
        StringBuilder tagsJson = new StringBuilder("[");
        for (int i = 0; i < cluster.size(); i++) {
            ClusterTag tag = cluster.get(i);
            if (i > 0) {
                tagsJson.append(',');
            }
            tagsJson.append("{\"id\":")
                    .append(tag.id())
                    .append(",\"name\":")
                    .append(quote(tag.name()))
                    .append('}');
        }
        tagsJson.append(']');

        String modeRules = switch (aggressiveness) {
            case CONSERVATIVE -> """
                    - Merge only exact duplicates, spelling variants, Simplified/Traditional variants, or translation variants.
                    - Do NOT merge parent/child, action/object, or different specific filters.
                    """;
            case STANDARD -> """
                    - Merge exact duplicates, spelling variants, Simplified/Traditional variants, translation variants, and common short/full forms.
                    - Examples to merge: \u9a91\u4e58/\u9a91\u4e58\u4f4d, \u9996\u6b21/\u9996\u6b21\u62cd\u6444.
                    - Keep separate: \u996e\u7cbe/\u996e\u9152/\u996e\u5c3f, \u6309\u6469/\u6309\u6469\u68d2.
                    """;
            case AGGRESSIVE -> """
                    - Merge tags that should be represented by one reusable library tag, including short/full forms and qualifier variants.
                    - Examples to merge: \u9a91\u4e58/\u9a91\u4e58\u4f4d, \u9996\u6b21/\u9996\u6b21\u62cd\u6444, \u62a5\u9053/\u5831\u5c0e, \u675f\u7f1a/\u6346\u7ed1.
                    - Keep separate: \u996e\u7cbe/\u996e\u9152/\u996e\u5c3f, \u6309\u6469/\u6309\u6469\u68d2, \u6311\u6218/\u6311\u8845, \u6392\u4fbf/\u6392\u5c3f.
                    """;
        };
        double minConfidence = minConfidence(aggressiveness);

        return """
                You are cleaning media-library tags. Group ONLY tags that should be represented by one reusable tag.
                Return ONLY raw JSON, no markdown, no explanation.
                JSON format: [{"canonicalId":1,"duplicateIds":[2,3],"confidence":0.92,"reason":"same concept"}]
                Strict rules:
                %s
                - canonicalId must be the best existing tag id in the group, preferably concise Simplified Chinese.
                - Only output groups with confidence >= %.2f.
                Tags:
                %s
                """.formatted(modeRules, minConfidence, tagsJson);
    }

    private List<SemanticMergeGroup> parseGroups(
            String raw,
            List<ClusterTag> cluster,
            MergeAggressiveness aggressiveness) {
        String cleaned = cleanJson(raw);
        if (cleaned.isBlank()) {
            return List.of();
        }
        Map<Integer, ClusterTag> byId = new LinkedHashMap<>();
        for (ClusterTag tag : cluster) {
            byId.put(tag.id(), tag);
        }
        double minConfidence = minConfidence(aggressiveness);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                return List.of();
            }
            List<SemanticMergeGroup> groups = new ArrayList<>();
            for (JsonNode node : root) {
                double confidence = node.path("confidence").asDouble(0.0);
                if (confidence < minConfidence) {
                    continue;
                }
                Integer canonicalId = node.path("canonicalId").canConvertToInt()
                        ? node.path("canonicalId").asInt()
                        : null;
                if (canonicalId == null || !byId.containsKey(canonicalId)) {
                    continue;
                }
                List<Integer> duplicateIds = new ArrayList<>();
                JsonNode duplicates = node.path("duplicateIds");
                if (duplicates.isArray()) {
                    for (JsonNode duplicate : duplicates) {
                        if (!duplicate.canConvertToInt()) {
                            continue;
                        }
                        int duplicateId = duplicate.asInt();
                        ClusterTag duplicateTag = byId.get(duplicateId);
                        ClusterTag canonicalTag = byId.get(canonicalId);
                        if (duplicateId != canonicalId
                                && duplicateTag != null
                                && canonicalTag != null
                                && byId.containsKey(duplicateId)
                                && !tagSimilarityService.shouldBlockMerge(
                                        canonicalTag.name(), duplicateTag.name())) {
                            duplicateIds.add(duplicateId);
                        }
                    }
                }
                if (!duplicateIds.isEmpty()) {
                    String reason = node.path("reason").asText("ai");
                    groups.add(new SemanticMergeGroup(canonicalId, List.copyOf(duplicateIds), confidence, reason));
                }
            }
            return groups;
        } catch (Exception e) {
            log.warn("Failed to parse AI semantic tag merge result: {} (raw={})", e.getMessage(), raw);
            return List.of();
        }
    }

    private List<SemanticMergeGroup> removeConflicts(List<SemanticMergeGroup> groups) {
        List<SemanticMergeGroup> result = new ArrayList<>();
        Set<Integer> usedIds = new HashSet<>();
        for (SemanticMergeGroup group : groups.stream()
                .sorted((left, right) -> Double.compare(right.confidence(), left.confidence()))
                .toList()) {
            if (usedIds.contains(group.canonicalId())) {
                continue;
            }
            List<Integer> duplicateIds = group.duplicateIds().stream()
                    .filter(id -> !usedIds.contains(id))
                    .distinct()
                    .toList();
            if (duplicateIds.isEmpty()) {
                continue;
            }
            result.add(new SemanticMergeGroup(
                    group.canonicalId(), duplicateIds, group.confidence(), group.reason()));
            usedIds.add(group.canonicalId());
            usedIds.addAll(duplicateIds);
        }
        return result;
    }

    private double minConfidence(MergeAggressiveness aggressiveness) {
        return switch (aggressiveness) {
            case CONSERVATIVE -> MIN_CONFIDENCE_CONSERVATIVE;
            case STANDARD -> MIN_CONFIDENCE_STANDARD;
            case AGGRESSIVE -> MIN_CONFIDENCE_AGGRESSIVE;
        };
    }

    private String findName(List<TagMergeSnapshot> tags, Integer id) {
        return tags.stream()
                .filter(tag -> id.equals(tag.id()))
                .map(TagMergeSnapshot::name)
                .findFirst()
                .orElse("");
    }

    private String cleanJson(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.contains("```")) {
            int start = cleaned.indexOf("```");
            int firstLineBreak = cleaned.indexOf('\n', start);
            int end = firstLineBreak >= 0 ? cleaned.indexOf("```", firstLineBreak) : -1;
            if (firstLineBreak >= 0 && end > firstLineBreak) {
                cleaned = cleaned.substring(firstLineBreak + 1, end).trim();
            }
        }
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            cleaned = cleaned.substring(arrayStart, arrayEnd + 1);
        }
        return cleaned;
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : "");
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
