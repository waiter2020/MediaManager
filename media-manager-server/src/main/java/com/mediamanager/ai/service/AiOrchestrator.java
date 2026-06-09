package com.mediamanager.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.provider.NoopAiProvider;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import com.mediamanager.system.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AiOrchestrator {

    private final List<AiProvider> providers;
    private final NoopAiProvider noopAiProvider;
    private final SysConfigService sysConfigService;
    private final LibraryPluginConfigRepository pluginConfigRepository;
    private final AiSuggestionService aiSuggestionService;
    private final MediaItemRepository mediaItemRepository;
    private final ObjectMapper objectMapper;
    private final AiHealthCheckScheduler healthCheckScheduler;

    public AiOrchestrator(
            List<AiProvider> providers,
            NoopAiProvider noopAiProvider,
            SysConfigService sysConfigService,
            LibraryPluginConfigRepository pluginConfigRepository,
            @Lazy AiSuggestionService aiSuggestionService,
            MediaItemRepository mediaItemRepository,
            ObjectMapper objectMapper,
            @Lazy AiHealthCheckScheduler healthCheckScheduler) {
        this.providers = providers;
        this.noopAiProvider = noopAiProvider;
        this.sysConfigService = sysConfigService;
        this.pluginConfigRepository = pluginConfigRepository;
        this.aiSuggestionService = aiSuggestionService;
        this.mediaItemRepository = mediaItemRepository;
        this.objectMapper = objectMapper;
        this.healthCheckScheduler = healthCheckScheduler;
    }

    @Value("${mediamanager.ai.default-provider:ollama}")
    private String yamlDefaultProviderId;

    @Value("${mediamanager.ai.llm-provider:${mediamanager.ai.default-provider:ollama}}")
    private String yamlLlmProviderId;

    @Value("${mediamanager.ai.embed-provider:${mediamanager.ai.default-provider:ollama}}")
    private String yamlEmbedProviderId;

    @Value("${mediamanager.ai.ollama.base-url:http://localhost:11434}")
    private String yamlOllamaBaseUrl;

    public AiProvider resolve(AiTaskType taskType) {
        return resolve(null, taskType);
    }

    public AiProvider resolve(Integer libraryId, AiTaskType taskType) {
        Map<String, Object> cfg = buildConfig(libraryId, taskType);
        String providerId = str(cfg, "providerId", yamlDefaultProviderId);
        boolean outboundAllowed = bool(cfg, "outboundAllowed", true);
        if (!outboundAllowed && "openai-compatible".equalsIgnoreCase(providerId)) {
            log.warn(
                    "AI provider openai-compatible is configured for {} but ai.outbound_allowed=false; "
                            + "refusing cloud provider",
                    taskType);
            return noopFor(taskType);
        }
        final String selectedId = providerId;
        return providers.stream()
                .filter(p -> !"noop".equals(p.providerId()))
                .filter(p -> p.supports(taskType))
                .filter(p -> p.providerId().equalsIgnoreCase(selectedId))
                .findFirst()
                .orElseGet(() -> {
                    log.warn(
                            "AI provider '{}' not available for {}; no cross-vendor fallback",
                            selectedId,
                            taskType);
                    return noopFor(taskType);
                });
    }

    private AiProvider noopFor(AiTaskType taskType) {
        if (noopAiProvider.supports(taskType)) {
            return noopAiProvider;
        }
        return providers.stream()
                .filter(p -> "noop".equals(p.providerId()))
                .findFirst()
                .orElse(noopAiProvider);
    }

    public Map<String, Object> defaultConfig() {
        return buildConfig(null, null);
    }

    public Map<String, Object> defaultConfig(Integer libraryId) {
        return buildConfig(libraryId, null);
    }

    public Map<String, Object> defaultConfig(AiTaskType taskType) {
        return defaultConfig(null, taskType);
    }

    public Map<String, Object> defaultConfig(Integer libraryId, AiTaskType taskType) {
        return buildConfig(libraryId, taskType);
    }

    private Map<String, Object> buildConfig(Integer libraryId, AiTaskType taskType) {
        Map<String, Object> cfg = new HashMap<>(sysConfigService.aiConfigOverrides());
        if (libraryId != null) {
            mergeLibraryAiConfig(libraryId, cfg);
        }
        String defaultProviderId = firstNonBlank(str(cfg, "providerId", null), yamlDefaultProviderId);
        String llmProviderId = firstNonBlank(str(cfg, "llmProviderId", null), yamlLlmProviderId, defaultProviderId);
        String embedProviderId = firstNonBlank(str(cfg, "embedProviderId", null), yamlEmbedProviderId, defaultProviderId);
        String selectedProviderId = providerIdForTask(taskType, defaultProviderId, llmProviderId, embedProviderId);
        cfg.put("defaultProviderId", defaultProviderId);
        cfg.put("llmProviderId", llmProviderId);
        cfg.put("embedProviderId", embedProviderId);
        cfg.put("providerId", selectedProviderId);
        cfg.put("baseUrl", baseUrlFor(selectedProviderId, taskType, cfg));
        String apiKey = apiKeyFor(selectedProviderId, taskType, cfg);
        if (!apiKey.isBlank() || cfg.containsKey("apiKey")) {
            cfg.put("apiKey", apiKey);
        }
        if (!cfg.containsKey("llmModel")) {
            cfg.put("llmModel", "qwen2.5:7b");
        }
        if (!cfg.containsKey("embedModel")) {
            cfg.put("embedModel", "nomic-embed-text");
        }
        return cfg;
    }

    private void mergeLibraryAiConfig(Integer libraryId, Map<String, Object> cfg) {
        List<LibraryPluginConfig> configs = pluginConfigRepository
                .findByLibrary_IdAndKindAndEnabledTrueOrderByPriorityAsc(libraryId, "AI_PROVIDER");
        if (configs.isEmpty()) {
            return;
        }
        LibraryPluginConfig first = configs.get(0);
        if (first.getPluginId() != null && !first.getPluginId().isBlank()) {
            cfg.put("providerId", first.getPluginId());
        }
        if (first.getConfig() != null && !first.getConfig().isBlank()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(
                        first.getConfig(), new TypeReference<Map<String, Object>>() {});
                parsed.forEach((k, v) -> {
                    if (v != null) {
                        cfg.put(normalizeConfigKey(k), v);
                    }
                });
                String baseUrl = str(cfg, "baseUrl", "");
                if (!baseUrl.isBlank()) {
                    if ("openai-compatible".equalsIgnoreCase(first.getPluginId())) {
                        cfg.put("openaiBaseUrl", baseUrl);
                    } else if ("ollama".equalsIgnoreCase(first.getPluginId())) {
                        cfg.put("ollamaBaseUrl", baseUrl);
                    }
                }
            } catch (Exception e) {
                log.warn("Invalid AI_PROVIDER config for library {}: {}", libraryId, e.getMessage());
            }
        }
    }

    public float[] embedText(String text) {
        return embedText(text, null);
    }

    public float[] embedText(String text, Integer libraryId) {
        return resolve(libraryId, AiTaskType.EMBED_TEXT)
                .embedText(text, defaultConfig(libraryId, AiTaskType.EMBED_TEXT));
    }

    public List<float[]> embedTexts(List<String> texts, Integer libraryId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return resolve(libraryId, AiTaskType.EMBED_TEXT)
                .embedTexts(texts, defaultConfig(libraryId, AiTaskType.EMBED_TEXT));
    }

    public Map<String, Object> embedConfig(Integer libraryId) {
        return defaultConfig(libraryId, AiTaskType.EMBED_TEXT);
    }

    public String embedModelId(Integer libraryId) {
        Map<String, Object> config = embedConfig(libraryId);
        String providerId = String.valueOf(config.getOrDefault("providerId", "noop"));
        String modelId = String.valueOf(config.getOrDefault("embedModel", "text-embedding-3-small"));
        return providerId + ":" + modelId;
    }

    public boolean isEmbeddingAvailable() {
        try {
            var health = healthCheckScheduler.getCachedHealth();
            return "ok".equals(health.get("status"));
        } catch (Exception e) {
            log.warn("Failed to check cached embedding status, falling back to direct check: {}", e.getMessage());
            try {
                return embedText("ping").length > 0;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    public String activeProviderId() {
        return resolve(AiTaskType.EMBED_TEXT).providerId();
    }

    public Optional<Map<String, Object>> parseNaturalLanguage(String query) {
        return resolve(AiTaskType.NL_QUERY)
                .parseNaturalLanguage(query, defaultConfig(AiTaskType.NL_QUERY));
    }

    public void completeMetadataIfNeeded(MediaItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        if (item.getOverview() != null && !item.getOverview().isBlank()) {
            return;
        }
        Integer libraryId = item.getLibrary() != null ? item.getLibrary().getId() : null;
        AiProvider provider = resolve(libraryId, AiTaskType.COMPLETE_METADATA);
        if ("noop".equals(provider.providerId())) {
            return;
        }
        String prompt = "Write a concise overview in the same language as the title for this media: "
                + item.getTitle();
        provider.completeMetadata(prompt, defaultConfig(libraryId, AiTaskType.COMPLETE_METADATA)).ifPresent(overview ->
                aiSuggestionService.createSuggestion(item, "overview", overview, provider.providerId(), 0.8f));
    }

    public void completeMetadataAsync(Integer itemId) {
        mediaItemRepository.findById(itemId).ifPresent(this::completeMetadataIfNeeded);
    }

    public boolean isClassifierEnabled() {
        Map<String, Object> cfg = sysConfigService.aiConfigOverrides();
        Object v = cfg.get("classifierEnabled");
        if (v instanceof Boolean b) {
            return b;
        }
        return true;
    }

    private static String str(Map<String, Object> cfg, String key, String def) {
        Object v = cfg.get(key);
        return v != null ? String.valueOf(v) : def;
    }

    private String providerIdForTask(
            AiTaskType taskType,
            String defaultProviderId,
            String llmProviderId,
            String embedProviderId) {
        if (taskType == AiTaskType.EMBED_TEXT) {
            return embedProviderId;
        }
        if (taskType == AiTaskType.COMPLETE_METADATA
                || taskType == AiTaskType.SUGGEST_TAGS
                || taskType == AiTaskType.NL_QUERY) {
            return llmProviderId;
        }
        return defaultProviderId;
    }

    private String baseUrlFor(String providerId, AiTaskType taskType, Map<String, Object> cfg) {
        if ("openai-compatible".equalsIgnoreCase(providerId)) {
            String baseUrl = firstNonBlank(
                    openAiTaskValue(taskType, cfg, "openaiLlmBaseUrl", "openaiEmbedBaseUrl"),
                    str(cfg, "openaiBaseUrl", null),
                    str(cfg, "baseUrl", null),
                    "");
            if (baseUrl.isBlank()) {
                log.warn("openai-compatible selected but ai.openai.base_url is empty");
            }
            return baseUrl;
        }
        if ("ollama".equalsIgnoreCase(providerId)) {
            return firstNonBlank(str(cfg, "ollamaBaseUrl", null), str(cfg, "baseUrl", null), yamlOllamaBaseUrl);
        }
        return str(cfg, "baseUrl", "");
    }

    private String apiKeyFor(String providerId, AiTaskType taskType, Map<String, Object> cfg) {
        if (!"openai-compatible".equalsIgnoreCase(providerId)) {
            return str(cfg, "apiKey", "");
        }
        return firstNonBlank(
                openAiTaskValue(taskType, cfg, "llmApiKey", "embedApiKey"),
                str(cfg, "apiKey", null),
                "");
    }

    private static String openAiTaskValue(
            AiTaskType taskType,
            Map<String, Object> cfg,
            String llmKey,
            String embedKey) {
        if (taskType == AiTaskType.EMBED_TEXT) {
            return str(cfg, embedKey, null);
        }
        if (taskType == AiTaskType.COMPLETE_METADATA
                || taskType == AiTaskType.SUGGEST_TAGS
                || taskType == AiTaskType.NL_QUERY) {
            return str(cfg, llmKey, null);
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String normalizeConfigKey(String key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case "base_url" -> "baseUrl";
            case "ollama_base_url" -> "ollamaBaseUrl";
            case "openai_base_url" -> "openaiBaseUrl";
            case "llm_base_url", "llmBaseUrl", "openai_llm_base_url", "openai.llm.base_url" -> "openaiLlmBaseUrl";
            case "embed_base_url", "embedBaseUrl", "embedding_base_url", "openai_embed_base_url", "openai.embed.base_url" -> "openaiEmbedBaseUrl";
            case "llm_model" -> "llmModel";
            case "embed_model" -> "embedModel";
            case "api_key" -> "apiKey";
            case "openai_api_key" -> "apiKey";
            case "llm_api_key", "llmApiKey", "openai_llm_api_key", "openai.llm.api_key" -> "llmApiKey";
            case "embed_api_key", "embedApiKey", "embedding_api_key", "openai_embed_api_key", "openai.embed.api_key" -> "embedApiKey";
            case "provider_id" -> "providerId";
            case "llmProvider", "llm_provider", "llm_provider_id" -> "llmProviderId";
            case "embedProvider", "embed_provider", "embed_provider_id" -> "embedProviderId";
            case "outbound_allowed" -> "outboundAllowed";
            case "timeout_ms" -> "timeoutMs";
            default -> key;
        };
    }

    private static boolean bool(Map<String, Object> cfg, String key, boolean def) {
        Object v = cfg.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v != null) {
            return "true".equalsIgnoreCase(String.valueOf(v));
        }
        return def;
    }
}
