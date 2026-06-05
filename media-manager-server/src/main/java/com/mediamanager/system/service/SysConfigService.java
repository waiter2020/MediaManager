package com.mediamanager.system.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.system.dto.AppearanceSettingsDto;
import com.mediamanager.system.dto.AppearanceSettingsUpdateRequest;
import com.mediamanager.system.dto.GeneralSettingsDto;
import com.mediamanager.system.dto.IntegrationsSettingsDto;
import com.mediamanager.system.dto.IntegrationsSettingsUpdateRequest;
import com.mediamanager.system.dto.MediaProcessingSettingsDto;
import com.mediamanager.system.dto.MediaProcessingSettingsUpdateRequest;
import com.mediamanager.system.dto.SecuritySettingsDto;
import com.mediamanager.system.dto.SecuritySettingsUpdateRequest;
import com.mediamanager.system.entity.SysConfig;
import com.mediamanager.system.repository.SysConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysConfigService {

    private static final Set<String> MASKED_KEYS = Set.of(
            "ai.openai.api_key",
            "ai.openai.llm.api_key",
            "ai.openai.embed.api_key",
            "tmdb.api_key"
    );

    private static final Set<String> ALLOWED_THEMES = Set.of("dark", "light", "system");

    private final SysConfigRepository configRepository;

    @Value("${mediamanager.auth.enabled:true}")
    private boolean yamlAuthEnabled;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.ffprobe.path:ffprobe}")
    private String yamlFfprobePath;

    @Value("${mediamanager.ai.ollama.base-url:http://localhost:11434}")
    private String yamlOllamaBaseUrl;

    private volatile Map<String, String> cache = Map.of();
    private volatile Boolean effectiveAuthEnabled;

    @PostConstruct
    void init() {
        refreshCache();
    }

    public void refreshCache() {
        cache = configRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(
                        SysConfig::getConfigKey,
                        c -> c.getConfigValue() != null ? c.getConfigValue() : "",
                        (a, b) -> b));
    }

    public String getString(String key, String defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return v;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    public int getInt(String key, int defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDouble(String key, double defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<Map<String, String>> listForApi() {
        return configRepository.findAll().stream()
                .map(c -> {
                    String value = c.getConfigValue() != null ? c.getConfigValue() : "";
                    if (MASKED_KEYS.contains(c.getConfigKey()) && !value.isBlank()) {
                        value = "***";
                    }
                    return Map.of(
                            "key", c.getConfigKey(),
                            "value", value,
                            "description", c.getDescription() != null ? c.getDescription() : ""
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateConfigs(Map<String, String> updates) {
        updateConfigs(updates, false);
    }

    @Transactional
    public void updateConfigs(Map<String, String> updates, boolean allowAiKeys) {
        updates.forEach((key, value) -> {
            if (!allowAiKeys && key != null && key.startsWith("ai.")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(),
                        "AI settings must be updated via PUT /api/v1/ai/config");
            }
            if (MASKED_KEYS.contains(key) && ("***".equals(value) || value == null || value.isBlank())) {
                return;
            }
            configRepository.findByConfigKey(key).ifPresent(config -> {
                config.setConfigValue(value);
                configRepository.save(config);
            });
        });
        refreshCache();
    }

    public GeneralSettingsDto getGeneralSettings(boolean setupCompleted) {
        return GeneralSettingsDto.builder()
                .version("1.0.0")
                .setupCompleted(setupCompleted)
                .build();
    }

    public SecuritySettingsDto getSecuritySettings() {
        boolean configuredAuthEnabled = isAuthEnabled(yamlAuthEnabled);
        boolean runningAuthEnabled = effectiveAuthEnabled != null
                ? effectiveAuthEnabled
                : configuredAuthEnabled;
        return SecuritySettingsDto.builder()
                .authEnabled(configuredAuthEnabled)
                .effectiveAuthEnabled(runningAuthEnabled)
                .requiresRestart(true)
                .restartRequired(configuredAuthEnabled != runningAuthEnabled)
                .build();
    }

    @Transactional
    public SecuritySettingsDto updateSecuritySettings(SecuritySettingsUpdateRequest request) {
        if (request.getAuthEnabled() != null) {
            updateSingle("auth.enabled", String.valueOf(request.getAuthEnabled()));
        }
        return getSecuritySettings();
    }

    public MediaProcessingSettingsDto getMediaProcessingSettings() {
        return MediaProcessingSettingsDto.builder()
                .ffmpegPath(ffmpegPath(yamlFfmpegPath))
                .ffprobePath(ffprobePath(yamlFfprobePath))
                .build();
    }

    @Transactional
    public MediaProcessingSettingsDto updateMediaProcessingSettings(MediaProcessingSettingsUpdateRequest request) {
        if (request.getFfmpegPath() != null) {
            String path = request.getFfmpegPath().trim();
            if (path.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "ffmpegPath must not be empty");
            }
            updateSingle("ffmpeg.path", path);
        }
        if (request.getFfprobePath() != null) {
            String path = request.getFfprobePath().trim();
            if (path.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "ffprobePath must not be empty");
            }
            updateSingle("ffprobe.path", path);
        }
        return getMediaProcessingSettings();
    }

    public IntegrationsSettingsDto getIntegrationsSettings() {
        String raw = rawConfigValue("tmdb.api_key");
        boolean configured = !raw.isBlank();
        return IntegrationsSettingsDto.builder()
                .tmdbApiKey(configured ? "***" : "")
                .tmdbApiKeyConfigured(configured)
                .build();
    }

    @Transactional
    public IntegrationsSettingsDto updateIntegrationsSettings(IntegrationsSettingsUpdateRequest request) {
        if (request.getTmdbApiKey() != null) {
            String key = request.getTmdbApiKey().trim();
            if ("***".equals(key) || key.isEmpty()) {
                return getIntegrationsSettings();
            }
            updateSingle("tmdb.api_key", key);
        }
        return getIntegrationsSettings();
    }

    public AppearanceSettingsDto getAppearanceSettings() {
        String theme = getString("ui.theme", "dark").trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_THEMES.contains(theme)) {
            theme = "dark";
        }
        return AppearanceSettingsDto.builder().theme(theme).build();
    }

    @Transactional
    public AppearanceSettingsDto updateAppearanceSettings(AppearanceSettingsUpdateRequest request) {
        if (request.getTheme() != null) {
            String theme = request.getTheme().trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_THEMES.contains(theme)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(),
                        "theme must be one of: dark, light, system");
            }
            updateSingle("ui.theme", theme);
        }
        return getAppearanceSettings();
    }

    private void updateSingle(String key, String value) {
        configRepository.findByConfigKey(key).ifPresent(config -> {
            config.setConfigValue(value);
            configRepository.save(config);
        });
        refreshCache();
    }

    public boolean isAuthEnabled(boolean yamlDefault) {
        return getBoolean("auth.enabled", yamlDefault);
    }

    public boolean isEffectiveAuthEnabled(boolean yamlDefault) {
        return effectiveAuthEnabled != null ? effectiveAuthEnabled : isAuthEnabled(yamlDefault);
    }

    public void captureEffectiveAuthEnabled(boolean authEnabled) {
        this.effectiveAuthEnabled = authEnabled;
    }

    public String ffmpegPath(String yamlDefault) {
        return getString("ffmpeg.path", yamlDefault);
    }

    public String ffprobePath(String yamlDefault) {
        return getString("ffprobe.path", yamlDefault);
    }

    public String tmdbApiKey() {
        return getString("tmdb.api_key", "");
    }

    public Map<String, Object> aiConfigOverrides() {
        Map<String, Object> cfg = new HashMap<>();
        String defaultProvider = getString("ai.default_provider", null);
        putIfPresent(cfg, "providerId", defaultProvider);
        putIfPresent(cfg, "llmProviderId", getString("ai.llm_provider", defaultProvider));
        putIfPresent(cfg, "embedProviderId", getString("ai.embed_provider", defaultProvider));
        putIfPresent(cfg, "ollamaBaseUrl", resolveOllamaBaseUrl());
        String openaiBaseUrl = getString("ai.openai.base_url", "");
        String openaiLlmBaseUrl = firstNonBlank(getString("ai.openai.llm.base_url", ""), openaiBaseUrl);
        String openaiEmbedBaseUrl = firstNonBlank(getString("ai.openai.embed.base_url", ""), openaiBaseUrl);
        putIfPresent(cfg, "openaiBaseUrl", openaiBaseUrl);
        putIfPresent(cfg, "openaiLlmBaseUrl", openaiLlmBaseUrl);
        putIfPresent(cfg, "openaiEmbedBaseUrl", openaiEmbedBaseUrl);
        putIfPresent(cfg, "llmModel", getString("ai.llm_model", null));
        putIfPresent(cfg, "embedModel", getString("ai.embed_model", null));
        String apiKey = getString("ai.openai.api_key", "");
        String llmApiKey = firstNonBlank(getString("ai.openai.llm.api_key", ""), apiKey);
        String embedApiKey = firstNonBlank(getString("ai.openai.embed.api_key", ""), apiKey);
        if (!apiKey.isBlank()) {
            cfg.put("apiKey", apiKey);
        }
        if (!llmApiKey.isBlank()) {
            cfg.put("llmApiKey", llmApiKey);
        }
        if (!embedApiKey.isBlank()) {
            cfg.put("embedApiKey", embedApiKey);
        }
        cfg.put("outboundAllowed", getBoolean("ai.outbound_allowed", true));
        cfg.put("classifierEnabled", getBoolean("ai.classifier.enabled", true));
        cfg.put("timeoutMs", getInt("ai.timeout_ms", 600000));
        cfg.put("autoApproveEnabled", getBoolean("ai.auto_approve.enabled", false));
        cfg.put("autoApproveConfidenceThreshold", getDouble("ai.auto_approve.confidence_threshold", 0.8));
        putIfPresent(cfg, "autoApproveFields", getString("ai.auto_approve.fields", "tag:*,overview"));
        return cfg;
    }

    private void putIfPresent(Map<String, Object> cfg, String key, String value) {
        if (value != null && !value.isBlank()) {
            cfg.put(key, value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public String effectiveOllamaBaseUrl() {
        String resolved = resolveOllamaBaseUrl();
        return resolved.isBlank() ? yamlOllamaBaseUrl : resolved;
    }

    private String resolveOllamaBaseUrl() {
        String db = rawConfigValue("ai.ollama.base_url");
        if (db.isBlank()) {
            return yamlOllamaBaseUrl;
        }
        if (isSeedLocalhostOllamaUrl(db) && !isSeedLocalhostOllamaUrl(yamlOllamaBaseUrl)) {
            return yamlOllamaBaseUrl;
        }
        return db;
    }

    private String rawConfigValue(String key) {
        String v = cache.get(key);
        return v != null ? v.trim() : "";
    }

    static boolean isSeedLocalhostOllamaUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String normalized = url.trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return "http://localhost:11434".equals(normalized)
                || "http://127.0.0.1:11434".equals(normalized);
    }
}
