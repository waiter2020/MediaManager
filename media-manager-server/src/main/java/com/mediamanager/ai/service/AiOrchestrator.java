package com.mediamanager.ai.service;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.ai.provider.NoopAiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiOrchestrator {

    private final List<AiProvider> providers;
    private final NoopAiProvider noopAiProvider;

    @Value("${mediamanager.ai.default-provider:ollama}")
    private String defaultProviderId;

    @Value("${mediamanager.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    public AiProvider resolve(AiTaskType taskType) {
        return providers.stream()
                .filter(p -> !"noop".equals(p.providerId()))
                .filter(p -> p.supports(taskType))
                .filter(p -> p.providerId().equals(defaultProviderId))
                .findFirst()
                .or(() -> providers.stream()
                        .filter(p -> !"noop".equals(p.providerId()))
                        .filter(p -> p.supports(taskType))
                        .findFirst())
                .orElse(noopAiProvider);
    }

    public Map<String, Object> defaultConfig() {
        return Map.of("baseUrl", ollamaBaseUrl, "llmModel", "qwen2.5:7b", "embedModel", "nomic-embed-text");
    }

    public float[] embedText(String text) {
        return resolve(AiTaskType.EMBED_TEXT).embedText(text, defaultConfig());
    }

    public Optional<Map<String, Object>> parseNaturalLanguage(String query) {
        return resolve(AiTaskType.NL_QUERY).parseNaturalLanguage(query, defaultConfig());
    }
}
