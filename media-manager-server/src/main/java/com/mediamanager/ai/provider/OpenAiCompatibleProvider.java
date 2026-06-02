package com.mediamanager.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleProvider implements AiProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 2;
    private static final long INITIAL_BACKOFF_MS = 1000;

    @Override
    public String providerId() {
        return "openai-compatible";
    }

    @Override
    public String displayName() {
        return "OpenAI Compatible API";
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return true;
    }

    @Override
    public float[] embedText(String text, Map<String, Object> config) {
        String baseUrl = str(config, "baseUrl", "https://api.openai.com/v1");
        String model = str(config, "embedModel", "text-embedding-3-small");
        String apiKey = str(config, "apiKey", "");

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = authHeaders(apiKey);
                Map<String, Object> body = Map.of("model", model, "input", text);
                String resp = restTemplate.postForObject(
                        baseUrl + "/embeddings", new HttpEntity<>(body, headers), String.class);
                JsonNode node = objectMapper.readTree(resp);
                JsonNode arr = node.path("data").path(0).path("embedding");
                if (!arr.isArray()) {
                    return new float[0];
                }
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    vec[i] = (float) arr.get(i).asDouble();
                }
                return vec;
            } catch (HttpStatusCodeException e) {
                log.warn("OpenAI embed HTTP error (status={}): {}", e.getStatusCode(), e.getResponseBodyAsString());
                if (e.getStatusCode().is4xxClientError()) {
                    // Fail fast on client errors
                    break;
                }
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("OpenAI embed attempt {}/{} failed ({}), retrying in {}ms...",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), backoff);
                    sleepQuietly(backoff);
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("OpenAI embed attempt {}/{} failed ({}), retrying in {}ms...",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), backoff);
                    sleepQuietly(backoff);
                } else {
                    log.warn("OpenAI embed failed after {} attempts: {}", MAX_RETRIES + 1, e.getMessage());
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
                log.warn("Failed to parse OpenAI-compatible natural language result: {} (Raw: {})", e.getMessage(), body);
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
        String baseUrl = str(config, "baseUrl", "https://api.openai.com/v1");
        String model = str(config, "llmModel", "gpt-4o-mini");
        String apiKey = str(config, "apiKey", "");

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpHeaders headers = authHeaders(apiKey);
                Map<String, Object> body = Map.of(
                        "model", model,
                        "messages", List.of(Map.of("role", "user", "content", prompt)));
                String resp = restTemplate.postForObject(
                        baseUrl + "/chat/completions", new HttpEntity<>(body, headers), String.class);
                JsonNode node = objectMapper.readTree(resp);
                return Optional.ofNullable(node.path("choices").path(0).path("message").path("content").asText(null));
            } catch (HttpStatusCodeException e) {
                log.warn("OpenAI chat HTTP error (status={}): {}", e.getStatusCode(), e.getResponseBodyAsString());
                if (e.getStatusCode().is4xxClientError()) {
                    // Fail fast on client errors
                    break;
                }
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("OpenAI chat attempt {}/{} failed ({}), retrying in {}ms...",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), backoff);
                    sleepQuietly(backoff);
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                    log.warn("OpenAI chat attempt {}/{} failed ({}), retrying in {}ms...",
                            attempt + 1, MAX_RETRIES + 1, e.getMessage(), backoff);
                    sleepQuietly(backoff);
                } else {
                    log.warn("OpenAI chat failed after {} attempts: {}", MAX_RETRIES + 1, e.getMessage());
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

    private HttpHeaders authHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    private String str(Map<String, Object> config, String key, String def) {
        if (config == null || !config.containsKey(key)) {
            return def;
        }
        return String.valueOf(config.get(key));
    }
}
