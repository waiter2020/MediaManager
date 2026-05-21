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

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleProvider implements AiProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
        return taskType != AiTaskType.EMBED_TEXT || true;
    }

    @Override
    public float[] embedText(String text, Map<String, Object> config) {
        String baseUrl = str(config, "baseUrl", "https://api.openai.com/v1");
        String model = str(config, "embedModel", "text-embedding-3-small");
        String apiKey = str(config, "apiKey", "");
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
        } catch (Exception e) {
            log.warn("OpenAI embed failed: {}", e.getMessage());
            return new float[0];
        }
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
        String prompt = "Return JSON only with keys keyword,type,minYear,maxYear,minRating for query: " + query;
        return chat(prompt, config).map(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(body, Map.class);
                return parsed;
            } catch (Exception e) {
                return Map.<String, Object>of("keyword", query);
            }
        });
    }

    private Optional<String> chat(String prompt, Map<String, Object> config) {
        String baseUrl = str(config, "baseUrl", "https://api.openai.com/v1");
        String model = str(config, "llmModel", "gpt-4o-mini");
        String apiKey = str(config, "apiKey", "");
        try {
            HttpHeaders headers = authHeaders(apiKey);
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));
            String resp = restTemplate.postForObject(
                    baseUrl + "/chat/completions", new HttpEntity<>(body, headers), String.class);
            JsonNode node = objectMapper.readTree(resp);
            return Optional.ofNullable(node.path("choices").path(0).path("message").path("content").asText(null));
        } catch (Exception e) {
            log.warn("OpenAI chat failed: {}", e.getMessage());
            return Optional.empty();
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
