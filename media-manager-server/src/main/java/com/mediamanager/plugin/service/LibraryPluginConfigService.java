package com.mediamanager.plugin.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LibraryPluginConfigService {

    private static final List<String> SCRAPER_IDS = List.of("tmdb", "javbus", "stashdb");

    /** Default plugin chain per library type (see docs/v2/06-catalog-and-library.md). */
    public static List<Map<String, Object>> defaultPluginsForType(String libraryType) {
        if (libraryType == null) {
            libraryType = "MIXED";
        }
        return switch (libraryType.toUpperCase()) {
            case "IMAGE" -> List.of(
                    defaultExtractorRow("exif", 0),
                    defaultExtractorRow("ffprobe", 10));
            case "AUDIO" -> List.of(
                    defaultExtractorRow("ffprobe", 0),
                    defaultExtractorRow("nfo", 10));
            case "MOVIE", "TV_SHOW" -> List.of(
                    defaultExtractorRow("nfo", 0),
                    defaultExtractorRow("ffprobe", 10),
                    defaultScraperRow("tmdb", 100));
            case "MIXED" -> List.of(
                    defaultExtractorRow("nfo", 0),
                    defaultExtractorRow("ffprobe", 10),
                    defaultExtractorRow("exif", 20),
                    defaultScraperRow("tmdb", 100));
            default -> List.of(
                    defaultExtractorRow("nfo", 0),
                    defaultExtractorRow("ffprobe", 10),
                    defaultScraperRow("tmdb", 100));
        };
    }

    private static Map<String, Object> defaultExtractorRow(String pluginId, int priority) {
        Map<String, Object> row = new HashMap<>();
        row.put("pluginId", pluginId);
        row.put("kind", PluginKind.EXTRACTOR.name());
        row.put("enabled", true);
        row.put("priority", priority);
        row.put("config", "{}");
        return row;
    }

    private static Map<String, Object> defaultScraperRow(String pluginId, int priority) {
        Map<String, Object> row = new HashMap<>();
        row.put("pluginId", pluginId);
        row.put("kind", PluginKind.SCRAPER.name());
        row.put("enabled", true);
        row.put("priority", priority);
        row.put("config", "{}");
        return row;
    }

    private final LibraryPluginConfigRepository repository;
    private final MediaLibraryRepository libraryRepository;
    private final LibraryAccessService libraryAccessService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForLibrary(Integer libraryId) {
        libraryAccessService.assertCanViewLibrary(libraryId);
        return repository.findByLibrary_IdOrderByPriorityAsc(libraryId).stream()
                .map(this::toMap)
                .toList();
    }

    /**
     * Mirror legacy {@code library_extractor_config} rows into {@code library_plugin_config}
     * so MetadataPipelineService uses the unified plugin table.
     */
    @Transactional
    public void syncFromExtractorConfigs(Integer libraryId) {
        MediaLibrary library = libraryRepository.findWithDetailsById(libraryId)
                .orElseGet(() -> libraryRepository.findById(libraryId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND)));
        List<Map<String, Object>> configs = library.getExtractorConfigs().stream()
                .map(e -> {
                    Map<String, Object> row = new java.util.HashMap<>();
                    String pluginId = e.getExtractorType().toLowerCase();
                    row.put("pluginId", pluginId);
                    row.put("kind", SCRAPER_IDS.contains(pluginId)
                            ? PluginKind.SCRAPER.name()
                            : PluginKind.EXTRACTOR.name());
                    row.put("enabled", e.getEnabled() != null ? e.getEnabled() : true);
                    row.put("priority", e.getPriority() != null ? e.getPriority() : 0);
                    row.put("config", e.getConfig() != null ? e.getConfig() : "");
                    return row;
                })
                .toList();
        if (configs.isEmpty()) {
            ensureDefaultExtractorConfigs(libraryId);
            return;
        }
        replaceConfigs(libraryId, configs);
    }

    /**
     * Seeds NFO / FFPROBE / TMDB when a library has no plugin or legacy extractor rows.
     */
    @Transactional
    public void ensureDefaultExtractorConfigs(Integer libraryId) {
        if (!repository.findByLibrary_IdOrderByPriorityAsc(libraryId).isEmpty()) {
            return;
        }
        MediaLibrary library = libraryRepository.findWithDetailsById(libraryId)
                .orElseGet(() -> libraryRepository.findById(libraryId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND)));
        if (library.getExtractorConfigs() != null && !library.getExtractorConfigs().isEmpty()) {
            syncFromExtractorConfigs(libraryId);
            return;
        }
        String type = library.getType() != null ? library.getType() : "MIXED";
        replaceConfigs(libraryId, defaultPluginsForType(type));
    }

    /**
     * Replaces library plugin rows with the default template for the library's current type.
     */
    @Transactional
    public List<Map<String, Object>> applyDefaultTemplate(Integer libraryId) {
        libraryAccessService.assertCanEditLibrary(libraryId);
        MediaLibrary library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));
        String type = library.getType() != null ? library.getType() : "MIXED";
        replaceConfigs(libraryId, defaultPluginsForType(type));
        return listForLibrary(libraryId);
    }

    @Transactional
    public void replaceConfigs(Integer libraryId, List<Map<String, Object>> configs) {
        libraryAccessService.assertCanEditLibrary(libraryId);
        MediaLibrary library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));

        List<LibraryPluginConfig> existing = repository.findByLibrary_IdOrderByPriorityAsc(libraryId);
        repository.deleteAll(existing);
        repository.flush();

        List<LibraryPluginConfig> toSave = new ArrayList<>();
        Map<String, LibraryPluginConfig> normalizedByKey = new LinkedHashMap<>();
        for (Map<String, Object> cfg : configs) {
            String pluginId = normalizePluginId(String.valueOf(cfg.get("pluginId")));
            String kind = normalizeKind(pluginId, String.valueOf(cfg.getOrDefault("kind", PluginKind.EXTRACTOR.name())));
            String key = kind + ":" + pluginId;
            if (normalizedByKey.containsKey(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Duplicate plugin config: " + pluginId + " (" + kind + ")");
            }
            normalizedByKey.put(key, LibraryPluginConfig.builder()
                    .library(library)
                    .pluginId(pluginId)
                    .kind(kind)
                    .enabled(cfg.get("enabled") == null || Boolean.parseBoolean(String.valueOf(cfg.get("enabled"))))
                    .priority(cfg.get("priority") != null ? Integer.parseInt(String.valueOf(cfg.get("priority"))) : 100)
                    .config(cfg.get("config") != null ? String.valueOf(cfg.get("config")) : null)
                    .build());
        }
        toSave.addAll(normalizedByKey.values());
        repository.saveAll(toSave);
    }

    /**
     * Legacy {@code library_extractor_config} sync — only when clients PUT {@code extractors[]} on the library.
     * Plugin API ({@link #replaceConfigs}) is the single source of truth; pipeline reads {@code library_plugin_config}.
     */
    @Transactional
    public void syncToExtractorConfigs(Integer libraryId) {
        MediaLibrary library = libraryRepository.findWithDetailsById(libraryId)
                .orElseGet(() -> libraryRepository.findById(libraryId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND)));
        library.getExtractorConfigs().clear();
        for (LibraryPluginConfig row : repository.findByLibrary_IdOrderByPriorityAsc(libraryId)) {
            if (!PluginKind.EXTRACTOR.name().equals(row.getKind())) {
                continue;
            }
            library.addExtractorConfig(LibraryExtractorConfig.builder()
                    .extractorType(row.getPluginId().toUpperCase())
                    .enabled(row.getEnabled())
                    .priority(row.getPriority())
                    .config(row.getConfig())
                    .build());
        }
        libraryRepository.save(library);
    }

    private Map<String, Object> toMap(LibraryPluginConfig c) {
        String pluginId = normalizePluginId(c.getPluginId());
        String kind = normalizeKind(pluginId, c.getKind());
        return Map.of(
                "pluginId", pluginId,
                "kind", kind,
                "enabled", c.getEnabled(),
                "priority", c.getPriority(),
                "config", c.getConfig() != null ? c.getConfig() : "");
    }

    private String normalizePluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank() || "null".equalsIgnoreCase(pluginId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "pluginId is required");
        }
        return pluginId.trim().toLowerCase();
    }

    private String normalizeKind(String pluginId, String kind) {
        if (SCRAPER_IDS.contains(pluginId)) {
            return PluginKind.SCRAPER.name();
        }
        if (kind == null || kind.isBlank() || "null".equalsIgnoreCase(kind)) {
            return PluginKind.EXTRACTOR.name();
        }
        return kind.trim().toUpperCase();
    }
}
