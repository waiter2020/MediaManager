package com.mediamanager.system.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.system.dto.AppearanceSettingsDto;
import com.mediamanager.system.dto.AppearanceSettingsUpdateRequest;
import com.mediamanager.system.dto.GeneralSettingsDto;
import com.mediamanager.system.dto.IntegrationsSettingsDto;
import com.mediamanager.system.dto.IntegrationsSettingsUpdateRequest;
import com.mediamanager.streaming.dto.HardwareAccelerationType;
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
            "tmdb.api_key",
            "opensubtitles.api_key",
            "opensubtitles.password"
    );

    private static final Set<String> ALLOWED_THEMES = Set.of("dark", "light", "system");

    private final SysConfigRepository configRepository;

    @Value("${mediamanager.auth.enabled:true}")
    private boolean yamlAuthEnabled;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.ffprobe.path:ffprobe}")
    private String yamlFfprobePath;

    @Value("${mediamanager.playback.hardware-acceleration:auto}")
    private String yamlHardwareAcceleration;

    @Value("${mediamanager.playback.hardware-device:/dev/dri/renderD128}")
    private String yamlHardwareDevice;

    @Value("${mediamanager.playback.hardware-encoder:}")
    private String yamlHardwareEncoder;

    @Value("${mediamanager.ai.ollama.base-url:http://localhost:11434}")
    private String yamlOllamaBaseUrl;

    @Value("${mediamanager.subtitle.default-language:zh-CN}")
    private String yamlSubtitleDefaultLanguage;

    @Value("${mediamanager.subtitle.user-agent:MediaManager/1.0}")
    private String yamlSubtitleUserAgent;

    @Value("${mediamanager.subtitle.opensubtitles.base-url:https://api.opensubtitles.com/api/v1}")
    private String yamlOpensubtitlesBaseUrl;

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
                .hardwareAcceleration(getString("playback.hardware_acceleration", yamlHardwareAcceleration))
                .hardwareDevice(getString("playback.hardware_device", yamlHardwareDevice))
                .hardwareEncoder(getString("playback.hardware_encoder", yamlHardwareEncoder))
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
        if (request.getHardwareAcceleration() != null) {
            String type = request.getHardwareAcceleration().trim().toLowerCase(Locale.ROOT);
            if (type.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "hardwareAcceleration must not be empty");
            }
            HardwareAccelerationType.from(type);
            updateSingle("playback.hardware_acceleration", type);
        }
        if (request.getHardwareDevice() != null) {
            String device = request.getHardwareDevice().trim();
            if (device.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "hardwareDevice must not be empty");
            }
            updateSingle("playback.hardware_device", device);
        }
        if (request.getHardwareEncoder() != null) {
            updateSingle("playback.hardware_encoder", request.getHardwareEncoder().trim());
        }
        return getMediaProcessingSettings();
    }

    public IntegrationsSettingsDto getIntegrationsSettings() {
        String tmdbRaw = rawConfigValue("tmdb.api_key");
        boolean tmdbConfigured = !tmdbRaw.isBlank();
        String osApiKeyRaw = rawConfigValue("opensubtitles.api_key");
        boolean osApiKeyConfigured = !osApiKeyRaw.isBlank();
        String osUsernameRaw = rawConfigValue("opensubtitles.username");
        boolean osUsernameConfigured = !osUsernameRaw.isBlank();
        String osPasswordRaw = rawConfigValue("opensubtitles.password");
        boolean osPasswordConfigured = !osPasswordRaw.isBlank();
        return IntegrationsSettingsDto.builder()
                .tmdbApiKey(tmdbConfigured ? "***" : "")
                .tmdbApiKeyConfigured(tmdbConfigured)
                .opensubtitlesApiKey(osApiKeyConfigured ? "***" : "")
                .opensubtitlesApiKeyConfigured(osApiKeyConfigured)
                .opensubtitlesUsername(osUsernameConfigured ? osUsernameRaw : "")
                .opensubtitlesUsernameConfigured(osUsernameConfigured)
                .opensubtitlesPassword(osPasswordConfigured ? "***" : "")
                .opensubtitlesPasswordConfigured(osPasswordConfigured)
                .subtitleDefaultLanguage(subtitleDefaultLanguage())
                .build();
    }

    @Transactional
    public IntegrationsSettingsDto updateIntegrationsSettings(IntegrationsSettingsUpdateRequest request) {
        if (request.getTmdbApiKey() != null) {
            String key = request.getTmdbApiKey().trim();
            if (!"***".equals(key) && !key.isEmpty()) {
                updateSingle("tmdb.api_key", key);
            }
        }
        if (request.getOpensubtitlesApiKey() != null) {
            String key = request.getOpensubtitlesApiKey().trim();
            if (!"***".equals(key) && !key.isEmpty()) {
                updateSingle("opensubtitles.api_key", key);
            }
        }
        if (request.getOpensubtitlesUsername() != null) {
            String username = request.getOpensubtitlesUsername().trim();
            if (!username.isEmpty()) {
                updateSingle("opensubtitles.username", username);
            }
        }
        if (request.getOpensubtitlesPassword() != null) {
            String password = request.getOpensubtitlesPassword().trim();
            if (!"***".equals(password) && !password.isEmpty()) {
                updateSingle("opensubtitles.password", password);
            }
        }
        if (request.getSubtitleDefaultLanguage() != null) {
            String language = request.getSubtitleDefaultLanguage().trim();
            if (!language.isEmpty()) {
                updateSingle("subtitle.default_language", language);
            }
        }
        return getIntegrationsSettings();
    }

    public String subtitleDefaultLanguage() {
        return getString("subtitle.default_language", yamlSubtitleDefaultLanguage);
    }

    public String subtitleUserAgent() {
        return getString("subtitle.user_agent", yamlSubtitleUserAgent);
    }

    public String opensubtitlesBaseUrl() {
        return yamlOpensubtitlesBaseUrl;
    }

    public String opensubtitlesApiKey() {
        return getString("opensubtitles.api_key", "");
    }

    public String opensubtitlesUsername() {
        return getString("opensubtitles.username", "");
    }

    public String opensubtitlesPassword() {
        return getString("opensubtitles.password", "");
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
        cfg.put("autoApproveConfidenceThreshold", getDouble("ai.auto_approve.confidence_threshold", 0.5));
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
