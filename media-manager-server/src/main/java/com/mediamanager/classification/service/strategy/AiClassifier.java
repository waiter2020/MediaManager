package com.mediamanager.classification.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.AiSuggestionService;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.MergeAggressiveness;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagMergeSnapshot;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.classification.service.TagSimilarityService;
import com.mediamanager.media.entity.MediaItem;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(300)
public class AiClassifier implements com.mediamanager.classification.spi.ClassifierStrategy {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_EXISTING_TAGS_IN_PROMPT = 250;
    private static final int MAX_OVERVIEW_CHARS = 360;
    private static final float SUGGESTION_CONFIDENCE = 0.85f;

    private final AiOrchestrator aiOrchestrator;
    private final AiSuggestionService aiSuggestionService;
    private final TagRepository tagRepository;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final TagSimilarityService tagSimilarityService;
    private final ObjectMapper objectMapper;

    public AiClassifier(
            @Lazy AiOrchestrator aiOrchestrator,
            @Lazy AiSuggestionService aiSuggestionService,
            TagRepository tagRepository,
            TagCanonicalizationService tagCanonicalizationService,
            TagQualityService tagQualityService,
            TagSimilarityService tagSimilarityService,
            ObjectMapper objectMapper) {
        this.aiOrchestrator = aiOrchestrator;
        this.aiSuggestionService = aiSuggestionService;
        this.tagRepository = tagRepository;
        this.tagCanonicalizationService = tagCanonicalizationService;
        this.tagQualityService = tagQualityService;
        this.tagSimilarityService = tagSimilarityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void classify(MediaItem item) {
        // Single-item classification is intentionally skipped for AI to avoid one LLM call per media item.
    }

    @Override
    public void classifyBatch(List<MediaItem> items) {
        if (!aiOrchestrator.isClassifierEnabled() || items == null || items.isEmpty()) {
            return;
        }
        Map<Integer, List<MediaItem>> byLibrary = groupClassifiableItems(items);
        if (byLibrary.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, List<MediaItem>> group : byLibrary.entrySet()) {
            Integer libraryId = group.getKey();
            AiProvider provider = aiOrchestrator.resolve(libraryId, AiTaskType.SUGGEST_TAGS);
            if ("noop".equals(provider.providerId())) {
                continue;
            }
            Map<String, Object> config = aiOrchestrator.defaultConfig(libraryId, AiTaskType.SUGGEST_TAGS);
            int batchSize = intValue(config, "classifierBatchSize", DEFAULT_BATCH_SIZE);
            Map<String, String> existingTagsByKey = existingTagsByKey();
            List<String> existingTagNames = existingTagsByKey.values().stream()
                    .limit(MAX_EXISTING_TAGS_IN_PROMPT)
                    .toList();
            List<MediaItem> groupItems = group.getValue();
            for (int start = 0; start < groupItems.size(); start += batchSize) {
                List<MediaItem> batch = groupItems.subList(start, Math.min(start + batchSize, groupItems.size()));
                classifyBatch(provider, config, batch, existingTagsByKey, existingTagNames);
            }
        }
    }

    private void classifyBatch(
            AiProvider provider,
            Map<String, Object> config,
            List<MediaItem> batch,
            Map<String, String> existingTagsByKey,
            List<String> existingTagNames) {
        if (batch.isEmpty()) {
            return;
        }
        boolean allowNewTags = boolValue(config, "classifierAllowNewTags", true);
        Optional<String> response = provider.completeMetadata(
                buildPrompt(batch, existingTagNames, allowNewTags),
                config);
        if (response.isEmpty()) {
            return;
        }
        Map<Integer, List<String>> tagsByItemId = parseBatchTags(response.get());
        Map<Integer, MediaItem> itemById = new LinkedHashMap<>();
        for (MediaItem item : batch) {
            itemById.put(item.getId(), item);
        }
        for (Map.Entry<Integer, List<String>> entry : tagsByItemId.entrySet()) {
            MediaItem item = itemById.get(entry.getKey());
            if (item == null) {
                continue;
            }
            for (String rawTag : entry.getValue()) {
                resolveSuggestedTagName(rawTag, existingTagsByKey, allowNewTags).ifPresent(tagName ->
                        aiSuggestionService.createSuggestion(
                                item,
                                "tag:" + tagName,
                                tagName,
                                provider.providerId(),
                                SUGGESTION_CONFIDENCE,
                                false));
            }
        }
        aiSuggestionService.autoApprovePendingForItems(itemById.keySet());
    }

    @Override
    public int getPriority() {
        return 300;
    }

    @Override
    public boolean runsInTransaction() {
        return false;
    }

    private Map<Integer, List<MediaItem>> groupClassifiableItems(List<MediaItem> items) {
        Map<Integer, List<MediaItem>> byLibrary = new LinkedHashMap<>();
        for (MediaItem item : items) {
            if (item == null
                    || item.getId() == null
                    || item.getTitle() == null
                    || item.getTitle().isBlank()
                    || !isVideo(item.getType())) {
                continue;
            }
            Integer libraryId = item.getLibrary() != null ? item.getLibrary().getId() : null;
            byLibrary.computeIfAbsent(libraryId, ignored -> new ArrayList<>()).add(item);
        }
        return byLibrary;
    }

    private boolean isVideo(String type) {
        return "MOVIE".equals(type) || "TV_SHOW".equals(type) || "EPISODE".equals(type);
    }

    private String buildPrompt(List<MediaItem> batch, List<String> existingTags, boolean allowNewTags) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("existingTags", existingTags);
        payload.put("allowNewTags", allowNewTags);
        payload.put("items", batch.stream().map(this::promptItem).toList());
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            jsonPayload = "{}";
        }
        return """
                You are a media tag classifier.
                Return ONLY a raw JSON array. Do not use markdown or explanations.
                Each array item must be: {"id": <media item id>, "tags": ["tag1", "tag2"]}.
                Use 2 to 5 relevant, broad, reusable tags per media item when the metadata supports them.
                Prefer an exact value from existingTags whenever it accurately describes the media, but never force an inaccurate existing tag.
                Add a new tag when existingTags do not cover an important reusable genre, theme, topic, mood, audience, format, or technical trait.
                New tags should enrich the shared vocabulary and be useful for multiple media items, not just the current item.
                Use Simplified Chinese names for new tags whenever possible.
                When allowNewTags is false, use ONLY exact values from existingTags and omit an item if none fit.
                When allowNewTags is true, new tags must be short reusable labels and should complement, not duplicate, existingTags.
                Never output apologies, disclaimers, sentences, descriptions, actor names, studio names, file names, one-off phrases, or prompt instructions.
                Input JSON:
                """ + jsonPayload;
    }

    private Map<String, String> existingTagsByKey() {
        Map<String, String> tagsByKey = new LinkedHashMap<>();
        tagRepository.findGlobalUsageCounts().stream()
                .filter(row -> row.getTagName() != null && !row.getTagName().isBlank())
                .filter(row -> tagQualityService.qualityIssue(row.getTagName()).isEmpty())
                .sorted((left, right) -> {
                    long leftUsage = left.getUsageCount() != null ? left.getUsageCount() : 0L;
                    long rightUsage = right.getUsageCount() != null ? right.getUsageCount() : 0L;
                    int usageCompare = Long.compare(rightUsage, leftUsage);
                    if (usageCompare != 0) {
                        return usageCompare;
                    }
                    return String.CASE_INSENSITIVE_ORDER.compare(left.getTagName(), right.getTagName());
                })
                .forEach(row -> {
                    String key = tagCanonicalizationService.semanticKey(row.getTagName());
                    if (!key.isBlank()) {
                        tagsByKey.putIfAbsent(key, row.getTagName());
                    }
                });
        return tagsByKey;
    }

    private Optional<String> resolveSuggestedTagName(
            String rawTag,
            Map<String, String> existingTagsByKey,
            boolean allowNewTags) {
        String displayName = tagCanonicalizationService.normalizeDisplayName(rawTag);
        if (displayName.isBlank() || !tagQualityService.isAcceptableAiTag(displayName)) {
            return Optional.empty();
        }
        String key = tagCanonicalizationService.semanticKey(displayName);
        if (existingTagsByKey.containsKey(key)) {
            return Optional.of(existingTagsByKey.get(key));
        }
        List<TagMergeSnapshot> snapshots = existingTagSnapshots();
        Optional<TagMergeSnapshot> structural = tagSimilarityService.findStructuralMatch(
                displayName, snapshots, MergeAggressiveness.AGGRESSIVE);
        if (structural.isPresent()) {
            return Optional.of(structural.get().name());
        }
        if (!allowNewTags && !existingTagsByKey.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(displayName);
    }

    private List<TagMergeSnapshot> existingTagSnapshots() {
        List<TagMergeSnapshot> snapshots = new ArrayList<>();
        tagRepository.findGlobalUsageCounts().forEach(row -> {
            if (row.getTagId() != null && row.getTagName() != null && !row.getTagName().isBlank()) {
                snapshots.add(new TagMergeSnapshot(
                        row.getTagId(),
                        row.getTagName(),
                        row.getUsageCount(),
                        row.getSource()));
            }
        });
        return snapshots;
    }

    private Map<String, Object> promptItem(MediaItem item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", item.getId());
        value.put("title", item.getTitle());
        value.put("type", item.getType());
        LocalDate releaseDate = item.getReleaseDate();
        if (releaseDate != null) {
            value.put("year", releaseDate.getYear());
        }
        if (item.getOverview() != null && !item.getOverview().isBlank()) {
            value.put("overview", truncate(item.getOverview().trim(), MAX_OVERVIEW_CHARS));
        }
        return value;
    }

    private Map<Integer, List<String>> parseBatchTags(String rawResponse) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(cleanJson(rawResponse));
            JsonNode itemsNode = root.isArray() ? root : firstArray(root, "items", "results", "data");
            if (itemsNode == null || !itemsNode.isArray()) {
                return result;
            }
            for (JsonNode itemNode : itemsNode) {
                Integer id = readId(itemNode);
                if (id == null) {
                    continue;
                }
                List<String> tags = readTags(itemNode.path("tags"));
                if (!tags.isEmpty()) {
                    result.put(id, tags);
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return result;
    }

    private JsonNode firstArray(JsonNode root, String... names) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = root.path(name);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private Integer readId(JsonNode itemNode) {
        JsonNode idNode = itemNode.path("id");
        if (idNode.isMissingNode()) {
            idNode = itemNode.path("mediaItemId");
        }
        if (idNode.isInt() || idNode.isLong()) {
            return idNode.asInt();
        }
        if (idNode.isTextual()) {
            try {
                return Integer.parseInt(idNode.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> readTags(JsonNode tagsNode) {
        if (tagsNode == null || tagsNode.isMissingNode() || tagsNode.isNull()) {
            return List.of();
        }
        if (tagsNode.isArray()) {
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : tagsNode) {
                if (tagNode.isTextual() && !tagNode.asText().isBlank()) {
                    tags.add(tagNode.asText().trim());
                }
            }
            return tags;
        }
        if (tagsNode.isTextual()) {
            return Arrays.stream(tagsNode.asText().split("[,;\\uFF0C\\uFF1B]"))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .toList();
        }
        return List.of();
    }

    private String cleanJson(String raw) {
        if (raw == null) {
            return "[]";
        }
        String cleaned = raw.trim();
        if (cleaned.contains("```")) {
            int firstFence = cleaned.indexOf("```");
            int firstNewline = cleaned.indexOf('\n', firstFence);
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return cleaned.substring(arrayStart, arrayEnd + 1);
        }
        int objectStart = cleaned.indexOf('{');
        int objectEnd = cleaned.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return cleaned.substring(objectStart, objectEnd + 1);
        }
        return cleaned;
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private int intValue(Map<String, Object> config, String key, int defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean boolValue(Map<String, Object> config, String key, boolean defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }
}
