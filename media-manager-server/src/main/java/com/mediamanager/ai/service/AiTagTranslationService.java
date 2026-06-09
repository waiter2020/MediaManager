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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTagTranslationService {

    private static final int AI_TRANSLATION_BATCH_SIZE = 100;
    private static final Pattern HAN_CHARACTER = Pattern.compile("\\p{IsHan}");
    private static final Pattern LATIN_CHARACTER = Pattern.compile("\\p{IsLatin}");
    private static final Pattern TECHNICAL_TAG = Pattern.compile(
            "^(h\\.?26[45]|x26[45]|hevc|avc|aac|dts|truehd|atmos|hdr|sdr|dv|dolbyvision|bluray|web-?dl)$",
            Pattern.CASE_INSENSITIVE);

    private final AiOrchestrator aiOrchestrator;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final ObjectMapper objectMapper;

    public List<AiOrganizationWorker.TagSnapshot> aiTranslationCandidates(
            List<AiOrganizationWorker.TagSnapshot> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && tag.id() != null)
                .filter(tag -> shouldAskAi(tag.name()))
                .toList();
    }

    public List<List<AiOrganizationWorker.TagSnapshot>> batches(
            List<AiOrganizationWorker.TagSnapshot> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<List<AiOrganizationWorker.TagSnapshot>> batches = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i += AI_TRANSLATION_BATCH_SIZE) {
            batches.add(candidates.subList(i, Math.min(i + AI_TRANSLATION_BATCH_SIZE, candidates.size())));
        }
        return batches;
    }

    public Map<Integer, String> translateBatch(
            Integer libraryId,
            List<AiOrganizationWorker.TagSnapshot> batch) {
        if (batch == null || batch.isEmpty()) {
            return Map.of();
        }
        AiProvider provider = aiOrchestrator.resolve(libraryId, AiTaskType.COMPLETE_METADATA);
        if ("noop".equalsIgnoreCase(provider.providerId())) {
            return Map.of();
        }
        return provider.completeMetadata(prompt(batch), aiOrchestrator.defaultConfig(libraryId, AiTaskType.COMPLETE_METADATA))
                .map(this::parseTranslations)
                .orElseGet(Map::of);
    }

    private boolean shouldAskAi(String rawName) {
        String name = tagCanonicalizationService.normalizeDisplayName(rawName);
        if (name.isBlank()
                || HAN_CHARACTER.matcher(name).find()
                || !LATIN_CHARACTER.matcher(name).find()
                || TECHNICAL_TAG.matcher(tagCanonicalizationService.semanticKey(name)).matches()
                || tagQualityService.qualityIssue(name).isPresent()) {
            return false;
        }
        return name.length() <= 64;
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
                Translate media-library tag names to concise Simplified Chinese.
                Return ONLY raw JSON, no markdown, no explanation.
                JSON format: [{"id":1,"zh":"动作"}]
                Rules:
                - Keep each result as a reusable tag, preferably 2-8 Chinese characters.
                - Preserve proper names, actor names, model names, codecs, resolutions, and acronyms when Chinese would be unnatural.
                - Avoid vulgar, explicit, conversational, or sentence-like wording.
                - If a tag should be preserved unchanged, return the original name in zh.
                Tags:
                %s
                """.formatted(tagsJson);
    }

    private Map<Integer, String> parseTranslations(String raw) {
        String cleaned = cleanJson(raw);
        if (cleaned.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            if (!root.isArray()) {
                return Map.of();
            }
            Map<Integer, String> translations = new LinkedHashMap<>();
            for (JsonNode node : root) {
                Integer id = node.path("id").canConvertToInt() ? node.path("id").asInt() : null;
                String zh = tagCanonicalizationService.normalizeDisplayName(node.path("zh").asText(""));
                if (id != null && !zh.isBlank()) {
                    translations.put(id, zh);
                }
            }
            return translations;
        } catch (Exception e) {
            log.warn("Failed to parse AI tag translation result: {} (raw={})", e.getMessage(), raw);
            return Map.of();
        }
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
}
