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

    private final RestTemplate restTemplate;
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

    @Override
    public float[] embedText(String text, Map<String, Object> config) {
        String baseUrl = str(config, "baseUrl", "http://localhost:11434");
        String model = str(config, "embedModel", "nomic-embed-text");
        try {
            Map<String, Object> body = Map.of("model", model, "prompt", text);
            String resp = restTemplate.postForObject(baseUrl + "/api/embeddings", body, String.class);
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
            log.warn("Ollama embed failed: {}", e.getMessage());
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
        String prompt = "Parse media search query into JSON with optional keys: keyword, type, minYear, maxYear, minRating. Query: " + query;
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
        String baseUrl = str(config, "baseUrl", "http://localhost:11434");
        String model = str(config, "llmModel", "qwen2.5:7b");
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false);
            String resp = restTemplate.postForObject(baseUrl + "/api/generate", body, String.class);
            JsonNode node = objectMapper.readTree(resp);
            return Optional.ofNullable(node.path("response").asText(null));
        } catch (Exception e) {
            log.warn("Ollama chat failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String str(Map<String, Object> config, String key, String def) {
        if (config == null || !config.containsKey(key)) {
            return def;
        }
        return String.valueOf(config.get(key));
    }
}
