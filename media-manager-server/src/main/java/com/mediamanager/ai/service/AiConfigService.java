package com.mediamanager.ai.service;

import com.mediamanager.ai.dto.AiConfigDto;
import com.mediamanager.ai.dto.AiConfigUpdateRequest;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final SysConfigService sysConfigService;
    private final List<AiProvider> providers;

    public List<Map<String, Object>> listProviders() {
        return providers.stream()
                .filter(p -> !"noop".equals(p.providerId()))
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", p.providerId());
                    row.put("displayName", p.displayName());
                    row.put("kind", "AI_PROVIDER");
                    row.put("local", "ollama".equals(p.providerId()));
                    row.put("configSchema", schemaFor(p.providerId()));
                    return row;
                })
                .toList();
    }

    public AiConfigDto getConfig() {
        return AiConfigDto.builder()
                .defaultProvider(sysConfigService.getString("ai.default_provider", "ollama"))
                .ollamaBaseUrl(sysConfigService.effectiveOllamaBaseUrl())
                .openaiBaseUrl(sysConfigService.getString("ai.openai.base_url", "https://api.openai.com/v1"))
                .openaiApiKey(maskApiKey(sysConfigService.getString("ai.openai.api_key", "")))
                .llmModel(sysConfigService.getString("ai.llm_model", "qwen2.5:7b"))
                .embedModel(sysConfigService.getString("ai.embed_model", "nomic-embed-text"))
                .classifierEnabled(sysConfigService.getBoolean("ai.classifier.enabled", true))
                .outboundAllowed(sysConfigService.getBoolean("ai.outbound_allowed", true))
                .timeoutMs(sysConfigService.getInt("ai.timeout_ms", 60000))
                .autoApproveEnabled(sysConfigService.getBoolean("ai.auto_approve.enabled", false))
                .autoApproveConfidenceThreshold(sysConfigService.getDouble("ai.auto_approve.confidence_threshold", 0.8))
                .autoApproveFields(sysConfigService.getString("ai.auto_approve.fields", "tag:*,overview"))
                .build();
    }

    @Transactional
    public AiConfigDto updateConfig(AiConfigUpdateRequest request) {
        Map<String, String> updates = new HashMap<>();
        if (request.getDefaultProvider() != null) {
            updates.put("ai.default_provider", request.getDefaultProvider().trim());
        }
        if (request.getOllamaBaseUrl() != null) {
            updates.put("ai.ollama.base_url", request.getOllamaBaseUrl().trim());
        }
        if ("openai-compatible".equalsIgnoreCase(
                request.getDefaultProvider() != null
                        ? request.getDefaultProvider().trim()
                        : sysConfigService.getString("ai.default_provider", "ollama"))) {
            // Avoid stale localhost Ollama URL confusing health checks and logs
            updates.put("ai.ollama.base_url", "");
        }
        if (request.getOpenaiBaseUrl() != null) {
            updates.put("ai.openai.base_url", request.getOpenaiBaseUrl().trim());
        }
        if (request.getOpenaiApiKey() != null && !"***".equals(request.getOpenaiApiKey().trim())) {
            updates.put("ai.openai.api_key", request.getOpenaiApiKey().trim());
        }
        if (request.getLlmModel() != null) {
            updates.put("ai.llm_model", request.getLlmModel().trim());
        }
        if (request.getEmbedModel() != null) {
            updates.put("ai.embed_model", request.getEmbedModel().trim());
        }
        if (request.getClassifierEnabled() != null) {
            updates.put("ai.classifier.enabled", request.getClassifierEnabled() ? "true" : "false");
        }
        if (request.getOutboundAllowed() != null) {
            updates.put("ai.outbound_allowed", request.getOutboundAllowed() ? "true" : "false");
        }
        if (request.getTimeoutMs() != null) {
            updates.put("ai.timeout_ms", String.valueOf(Math.max(5000, request.getTimeoutMs())));
        }
        if (request.getAutoApproveEnabled() != null) {
            updates.put("ai.auto_approve.enabled", request.getAutoApproveEnabled() ? "true" : "false");
        }
        if (request.getAutoApproveConfidenceThreshold() != null) {
            updates.put("ai.auto_approve.confidence_threshold", String.valueOf(request.getAutoApproveConfidenceThreshold()));
        }
        if (request.getAutoApproveFields() != null) {
            updates.put("ai.auto_approve.fields", request.getAutoApproveFields().trim());
        }
        sysConfigService.updateConfigs(updates, true);
        return getConfig();
    }

    private static String maskApiKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return "***";
    }

    private static Map<String, Object> schemaFor(String providerId) {
        if ("openai-compatible".equals(providerId)) {
            return Map.of(
                    "fields", List.of(
                            field("baseUrl", "API Base URL", "https://api.openai.com/v1"),
                            field("apiKey", "API Key", ""),
                            field("llmModel", "LLM 模型", "gpt-4o-mini"),
                            field("embedModel", "Embedding 模型", "text-embedding-3-small")
                    ));
        }
        return Map.of(
                "fields", List.of(
                        field("baseUrl", "Ollama 地址", "http://localhost:11434"),
                        field("llmModel", "LLM 模型", "qwen2.5:7b"),
                        field("embedModel", "Embedding 模型", "nomic-embed-text")
                ));
    }

    private static Map<String, String> field(String key, String label, String placeholder) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("label", label);
        f.put("placeholder", placeholder);
        return f;
    }
}
