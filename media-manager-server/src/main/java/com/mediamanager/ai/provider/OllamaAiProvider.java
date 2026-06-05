package com.mediamanager.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaAiProvider implements AiProvider {

    private final AiHttpClientFactory httpClientFactory;
    private final ObjectMapper objectMapper;

    @Override
    public String providerId() {
        return "ollama";
    }

    @Override
    public String displayName() {
        return "Ollama (Local)";
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return true;
    }

    private static final int MAX_RETRIES = 2;
    private static final long INITIAL_BACKOFF_MS = 500;

    @Override
    public float[] embedText(String text, Map<String, Object> config) {
        String baseUrl = str(config, "baseUrl", "http://localhost:11434");
        String model = str(config, "embedModel", "nomic-embed-text");
        long timeoutMs = longVal(config, "timeoutMs", AiHttpClientFactory.DEFAULT_TIMEOUT_MS);
        RestTemplate requestClient = httpClientFactory.create(timeoutMs);
        log.debug("Ollama embed request: model={}, timeoutMs={}", model, timeoutMs);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> body = Map.of("model", model, "prompt", text);
                String resp = requestClient.postForObject(baseUrl + "/api/embeddings", body, String.class);
                JsonNode node = objectMapper.readTree(resp);
                JsonNode embedding = node.path("embedding");
                if (!embedding.isArray()) {
                    return new float[0];
                }
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vec[i] = (float) embedding.get(i).asDouble();
                }
                return vec;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("Ollama embed attempt {}/{} failed ({}), retrying in {}ms...",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), backoff);
                    sleepQuietly(backoff);
                } else {
                    log.warn("Ollama embed failed after {} attempts (model={}, timeoutMs={}): {}",
                            MAX_RETRIES + 1, model, timeoutMs, e.getMessage());
                }
            }
        }
        return new float[0];
    }

    @Override
    public Optional<String> completeMetadata(String prompt, Map<String, Object> config) {
        return chat(prompt, config);
    }

    @Override
    public List<String> suggestTags(String prompt, Map<String, Object> config) {
        return chat(prompt, config)
                .map(s -> Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList())
                .orElse(Collections.emptyList());
    }

    @Override
    public Optional<Map<String, Object>> parseNaturalLanguage(String query, Map<String, Object> config) {
        String prompt = "Parse media search query into JSON with optional keys: keyword, type, minYear, maxYear, minRating. "
                + "Return ONLY a raw JSON block, no markdown formatting (no ```json or ```), no additional explanations or conversational filler. "
                + "Example: query \"2021年评分大于8的电影\" -> {\"keyword\":\"\",\"type\":\"MOVIE\",\"minYear\":2021,\"maxYear\":2021,\"minRating\":8.0}. "
                + "Query: " + query;
        return chat(prompt, config).map(body -> {
            try {
                String cleaned = cleanJsonString(body);
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(cleaned, Map.class);
                return parsed;
            } catch (Exception e) {
                log.warn("Failed to parse Ollama natural language result: {} (Raw: {})", e.getMessage(), body);
                return Map.<String, Object>of("keyword", query);
            }
        });
    }

    private String cleanJsonString(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.contains("```")) {
            int startIdx = cleaned.indexOf("```");
            if (startIdx != -1) {
                int newlineIdx = cleaned.indexOf("\n", startIdx);
                if (newlineIdx != -1) {
                    int endIdx = cleaned.indexOf("```", newlineIdx);
                    if (endIdx != -1) {
                        cleaned = cleaned.substring(newlineIdx + 1, endIdx).trim();
                    } else {
                        cleaned = cleaned.substring(newlineIdx + 1).trim();
                    }
                }
            }
        }
        return cleaned;
    }

    private Optional<String> chat(String prompt, Map<String, Object> config) {
        String baseUrl = str(config, "baseUrl", "http://localhost:11434");
        String model = str(config, "llmModel", "qwen2.5:7b");
        long timeoutMs = longVal(config, "timeoutMs", AiHttpClientFactory.DEFAULT_TIMEOUT_MS);
        RestTemplate requestClient = httpClientFactory.create(timeoutMs);
        log.debug("Ollama chat request: model={}, timeoutMs={}", model, timeoutMs);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Map<String, Object> body = Map.of(
                        "model", model,
                        "prompt", prompt,
                        "stream", false);
                String resp = requestClient.postForObject(baseUrl + "/api/generate", body, String.class);
                JsonNode node = objectMapper.readTree(resp);
                return Optional.ofNullable(node.path("response").asText(null));
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("Ollama chat attempt {}/{} failed ({}), retrying in {}ms...",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), backoff);
                    sleepQuietly(backoff);
                } else {
                    log.warn("Ollama chat failed after {} attempts (model={}, timeoutMs={}): {}",
                            MAX_RETRIES + 1, model, timeoutMs, e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private long longVal(Map<String, Object> config, String key, long def) {
        if (config == null || !config.containsKey(key)) {
            return def;
        }
        Object val = config.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private String str(Map<String, Object> config, String key, String def) {
        if (config == null || !config.containsKey(key)) {
            return def;
        }
        return String.valueOf(config.get(key));
    }
}
