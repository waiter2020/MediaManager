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
    private final AiSuggestionService aiSuggestionService;

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
        String defaultProvider = sysConfigService.getString("ai.default_provider", "ollama");
        String openaiBaseUrl = sysConfigService.getString("ai.openai.base_url", "https://api.openai.com/v1");
        String openaiApiKey = sysConfigService.getString("ai.openai.api_key", "");
        String openaiLlmBaseUrl = sysConfigService.getString("ai.openai.llm.base_url", openaiBaseUrl);
        String openaiEmbedBaseUrl = sysConfigService.getString("ai.openai.embed.base_url", openaiBaseUrl);
        String openaiLlmApiKey = firstNonBlank(sysConfigService.getString("ai.openai.llm.api_key", ""), openaiApiKey);
        String openaiEmbedApiKey = firstNonBlank(sysConfigService.getString("ai.openai.embed.api_key", ""), openaiApiKey);
        return AiConfigDto.builder()
                .defaultProvider(defaultProvider)
                .llmProvider(sysConfigService.getString("ai.llm_provider", defaultProvider))
                .embedProvider(sysConfigService.getString("ai.embed_provider", defaultProvider))
                .ollamaBaseUrl(sysConfigService.effectiveOllamaBaseUrl())
                .openaiBaseUrl(openaiBaseUrl)
                .openaiApiKey(maskApiKey(openaiApiKey))
                .openaiLlmBaseUrl(openaiLlmBaseUrl)
                .openaiLlmApiKey(maskApiKey(openaiLlmApiKey))
                .openaiEmbedBaseUrl(openaiEmbedBaseUrl)
                .openaiEmbedApiKey(maskApiKey(openaiEmbedApiKey))
                .llmModel(sysConfigService.getString("ai.llm_model", "qwen2.5:7b"))
                .embedModel(sysConfigService.getString("ai.embed_model", "nomic-embed-text"))
                .classifierEnabled(sysConfigService.getBoolean("ai.classifier.enabled", true))
                .outboundAllowed(sysConfigService.getBoolean("ai.outbound_allowed", true))
                .timeoutMs(sysConfigService.getInt("ai.timeout_ms", 600000))
                .autoApproveEnabled(sysConfigService.getBoolean("ai.auto_approve.enabled", false))
                .autoApproveConfidenceThreshold(sysConfigService.getDouble("ai.auto_approve.confidence_threshold", 0.5))
                .autoApproveFields(sysConfigService.getString("ai.auto_approve.fields", "tag:*,overview"))
                .build();
    }

    @Transactional
    public AiConfigDto updateConfig(AiConfigUpdateRequest request) {
        Map<String, String> updates = new HashMap<>();
        boolean previousAutoApproveEnabled = sysConfigService.getBoolean("ai.auto_approve.enabled", false);
        double previousAutoApproveThreshold = sysConfigService.getDouble("ai.auto_approve.confidence_threshold", 0.5);
        String previousAutoApproveFields = sysConfigService.getString("ai.auto_approve.fields", "tag:*,overview");
        String currentDefaultProvider = sysConfigService.getString("ai.default_provider", "ollama");
        String currentLlmProvider = sysConfigService.getString("ai.llm_provider", currentDefaultProvider);
        String currentEmbedProvider = sysConfigService.getString("ai.embed_provider", currentDefaultProvider);
        String requestedDefaultProvider = trimToNull(request.getDefaultProvider());
        String requestedLlmProvider = trimToNull(request.getLlmProvider());
        String requestedEmbedProvider = trimToNull(request.getEmbedProvider());
        String effectiveDefaultProvider = requestedDefaultProvider != null
                ? requestedDefaultProvider
                : currentDefaultProvider;
        String effectiveLlmProvider = requestedLlmProvider != null
                ? requestedLlmProvider
                : requestedDefaultProvider != null ? requestedDefaultProvider : currentLlmProvider;
        String effectiveEmbedProvider = requestedEmbedProvider != null
                ? requestedEmbedProvider
                : requestedDefaultProvider != null ? requestedDefaultProvider : currentEmbedProvider;

        if (requestedDefaultProvider != null) {
            updates.put("ai.default_provider", requestedDefaultProvider);
        } else if (requestedLlmProvider != null) {
            updates.put("ai.default_provider", requestedLlmProvider);
            effectiveDefaultProvider = requestedLlmProvider;
        }
        if (requestedLlmProvider != null || requestedDefaultProvider != null) {
            updates.put("ai.llm_provider", effectiveLlmProvider);
        }
        if (requestedEmbedProvider != null || requestedDefaultProvider != null) {
            updates.put("ai.embed_provider", effectiveEmbedProvider);
        }
        if (request.getOllamaBaseUrl() != null) {
            updates.put("ai.ollama.base_url", request.getOllamaBaseUrl().trim());
        }
        if (!usesOllama(effectiveDefaultProvider, effectiveLlmProvider, effectiveEmbedProvider)) {
            // Avoid stale localhost Ollama URL confusing health checks and logs
            updates.put("ai.ollama.base_url", "");
        }
        if (request.getOpenaiBaseUrl() != null) {
            updates.put("ai.openai.base_url", request.getOpenaiBaseUrl().trim());
        }
        if (shouldUpdateSecret(request.getOpenaiApiKey())) {
            updates.put("ai.openai.api_key", request.getOpenaiApiKey().trim());
        }
        if (request.getOpenaiLlmBaseUrl() != null) {
            updates.put("ai.openai.llm.base_url", request.getOpenaiLlmBaseUrl().trim());
        }
        if (shouldUpdateSecret(request.getOpenaiLlmApiKey())) {
            updates.put("ai.openai.llm.api_key", request.getOpenaiLlmApiKey().trim());
        }
        if (request.getOpenaiEmbedBaseUrl() != null) {
            updates.put("ai.openai.embed.base_url", request.getOpenaiEmbedBaseUrl().trim());
        }
        if (shouldUpdateSecret(request.getOpenaiEmbedApiKey())) {
            updates.put("ai.openai.embed.api_key", request.getOpenaiEmbedApiKey().trim());
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
        AiConfigDto updated = getConfig();
        if (shouldBackfillAutoApprovedSuggestions(
                request,
                updated,
                previousAutoApproveEnabled,
                previousAutoApproveThreshold,
                previousAutoApproveFields)) {
            aiSuggestionService.autoApprovePendingSuggestions();
        }
        return updated;
    }

    private boolean shouldBackfillAutoApprovedSuggestions(
            AiConfigUpdateRequest request,
            AiConfigDto updated,
            boolean previousEnabled,
            double previousThreshold,
            String previousFields) {
        if (!Boolean.TRUE.equals(updated.getAutoApproveEnabled())) {
            return false;
        }
        if (Boolean.TRUE.equals(request.getAutoApproveEnabled()) && !previousEnabled) {
            return true;
        }
        if (!previousEnabled) {
            return false;
        }
        if (request.getAutoApproveConfidenceThreshold() != null
                && Double.compare(request.getAutoApproveConfidenceThreshold(), previousThreshold) != 0) {
            return true;
        }
        return request.getAutoApproveFields() != null
                && !request.getAutoApproveFields().trim().equals(previousFields);
    }

    private static boolean usesOllama(String... providerIds) {
        for (String providerId : providerIds) {
            if ("ollama".equalsIgnoreCase(providerId)) {
                return true;
            }
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean shouldUpdateSecret(String value) {
        return value != null && !"***".equals(value.trim());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
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
                            field("openaiLlmBaseUrl", "LLM API Base URL", "https://api.openai.com/v1"),
                            field("llmApiKey", "LLM API Key", ""),
                            field("openaiEmbedBaseUrl", "Embedding API Base URL", "https://api.openai.com/v1"),
                            field("embedApiKey", "Embedding API Key", ""),
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
