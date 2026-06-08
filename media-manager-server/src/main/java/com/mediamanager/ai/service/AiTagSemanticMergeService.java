package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagQualityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTagSemanticMergeService {

    private static final int AI_MERGE_BATCH_SIZE = 100;
    private static final double MIN_CONFIDENCE = 0.86;

    private final AiOrchestrator aiOrchestrator;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final ObjectMapper objectMapper;

    public List<SemanticMergeGroup> suggestGroups(
            Integer libraryId,
            List<AiOrganizationWorker.TagSnapshot> tags) {
        List<AiOrganizationWorker.TagSnapshot> candidates = mergeCandidates(tags);
        if (candidates.isEmpty()) {
            return List.of();
        }
        AiProvider provider = aiOrchestrator.resolve(libraryId, AiTaskType.COMPLETE_METADATA);
        if ("noop".equalsIgnoreCase(provider.providerId())) {
            return List.of();
        }

        List<SemanticMergeGroup> groups = new ArrayList<>();
        for (List<AiOrganizationWorker.TagSnapshot> batch : batches(candidates)) {
            try {
                provider.completeMetadata(prompt(batch), aiOrchestrator.defaultConfig(libraryId, AiTaskType.COMPLETE_METADATA))
                        .map(raw -> parseGroups(raw, batch))
                        .ifPresent(groups::addAll);
            } catch (Exception e) {
                log.warn("Failed to generate AI semantic tag merge suggestions: {}", e.getMessage());
            }
        }
        return removeConflicts(groups);
    }

    private List<AiOrganizationWorker.TagSnapshot> mergeCandidates(List<AiOrganizationWorker.TagSnapshot> tags) {
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
                .toList();
    }

    private List<List<AiOrganizationWorker.TagSnapshot>> batches(
            List<AiOrganizationWorker.TagSnapshot> candidates) {
        List<List<AiOrganizationWorker.TagSnapshot>> batches = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += AI_MERGE_BATCH_SIZE) {
            batches.add(candidates.subList(i, Math.min(i + AI_MERGE_BATCH_SIZE, candidates.size())));
        }
        return batches;
    }

    private String prompt(List<AiOrganizationWorker.TagSnapshot> batch) {
        StringBuilder tagsJson = new StringBuilder("[");
        for (int i = 0; i < batch.size(); i++) {
            AiOrganizationWorker.TagSnapshot tag = batch.get(i);
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

        return """
                You are cleaning media-library tags. Group ONLY tags that should be represented by one reusable tag.
                Return ONLY raw JSON, no markdown, no explanation.
                JSON format: [{"canonicalId":1,"duplicateIds":[2,3],"confidence":0.92,"reason":"same concept"}]
                Strict rules:
                - Merge only exact duplicates, spelling variants, Simplified/Traditional variants, translation variants, or true synonyms.
                - Do NOT merge tags that are merely related, parent/child, action/object, cause/effect, scene co-occurrence, or different specific filters.
                - Keep these separate if they appear: 按摩 vs 按摩棒, 挑战 vs 挑衅, 排便 vs 排尿, 报道 vs 报复.
                - Good merge examples: 报导/报道, 批评/批判, 复仇/报复, 拷問/拷问/审问, 束缚/拘束/捆绑.
                - canonicalId must be the best existing tag id in the group, preferably concise Simplified Chinese.
                - Only output groups with confidence >= 0.86.
                Tags:
                %s
                """.formatted(tagsJson);
    }

    private List<SemanticMergeGroup> parseGroups(
            String raw,
            List<AiOrganizationWorker.TagSnapshot> batch) {
        String cleaned = cleanJson(raw);
        if (cleaned.isBlank()) {
            return List.of();
        }
        Map<Integer, AiOrganizationWorker.TagSnapshot> byId = new LinkedHashMap<>();
        for (AiOrganizationWorker.TagSnapshot tag : batch) {
            byId.put(tag.id(), tag);
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                return List.of();
            }
            List<SemanticMergeGroup> groups = new ArrayList<>();
            for (JsonNode node : root) {
                double confidence = node.path("confidence").asDouble(0.0);
                if (confidence < MIN_CONFIDENCE) {
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
                        if (duplicateId != canonicalId && byId.containsKey(duplicateId)) {
                            duplicateIds.add(duplicateId);
                        }
                    }
                }
                if (!duplicateIds.isEmpty()) {
                    groups.add(new SemanticMergeGroup(canonicalId, List.copyOf(duplicateIds), confidence));
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
            result.add(new SemanticMergeGroup(group.canonicalId(), duplicateIds, group.confidence()));
            usedIds.add(group.canonicalId());
            usedIds.addAll(duplicateIds);
        }
        return result;
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

    public record SemanticMergeGroup(Integer canonicalId, List<Integer> duplicateIds, double confidence) {
    }
}
