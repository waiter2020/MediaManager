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
import org.springframework.scheduling.annotation.Async;
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

    @Value("${mediamanager.ai.ollama.base-url:http://localhost:11434}")
    private String yamlOllamaBaseUrl;

    public AiProvider resolve(AiTaskType taskType) {
        return resolve(null, taskType);
    }

    public AiProvider resolve(Integer libraryId, AiTaskType taskType) {
        Map<String, Object> cfg = buildConfig(libraryId);
        String providerId = str(cfg, "providerId", yamlDefaultProviderId);
        boolean outboundAllowed = bool(cfg, "outboundAllowed", true);
        if (!outboundAllowed && "openai-compatible".equalsIgnoreCase(providerId)) {
            log.warn(
                    "AI provider openai-compatible is configured but ai.outbound_allowed=false; "
                            + "refusing silent fallback to Ollama");
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
        return defaultConfig(null);
    }

    public Map<String, Object> defaultConfig(Integer libraryId) {
        return buildConfig(libraryId);
    }

    private Map<String, Object> buildConfig(Integer libraryId) {
        Map<String, Object> cfg = new HashMap<>(sysConfigService.aiConfigOverrides());
        if (libraryId != null) {
            mergeLibraryAiConfig(libraryId, cfg);
        }
        String providerId = str(cfg, "providerId", yamlDefaultProviderId);
        if (!cfg.containsKey("providerId") || providerId.isBlank()) {
            cfg.put("providerId", yamlDefaultProviderId);
            providerId = yamlDefaultProviderId;
        }
        if (!cfg.containsKey("baseUrl") || str(cfg, "baseUrl", "").isBlank()) {
            if ("openai-compatible".equalsIgnoreCase(providerId)) {
                log.warn("openai-compatible selected but ai.openai.base_url is empty");
            } else {
                cfg.put("baseUrl", yamlOllamaBaseUrl);
            }
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
            } catch (Exception e) {
                log.warn("Invalid AI_PROVIDER config for library {}: {}", libraryId, e.getMessage());
            }
        }
    }

    public float[] embedText(String text) {
        return embedText(text, null);
    }

    public float[] embedText(String text, Integer libraryId) {
        return resolve(libraryId, AiTaskType.EMBED_TEXT).embedText(text, defaultConfig(libraryId));
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
        return resolve(AiTaskType.NL_QUERY).parseNaturalLanguage(query, defaultConfig());
    }

    @Async
    public void completeMetadataAsync(Integer itemId) {
        mediaItemRepository.findById(itemId).ifPresent(item -> {
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
            provider.completeMetadata(prompt, defaultConfig(libraryId)).ifPresent(overview ->
                    aiSuggestionService.createSuggestion(item, "overview", overview, provider.providerId(), 0.8f));
        });
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

    private static String normalizeConfigKey(String key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case "base_url" -> "baseUrl";
            case "llm_model" -> "llmModel";
            case "embed_model" -> "embedModel";
            case "api_key" -> "apiKey";
            case "provider_id" -> "providerId";
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
