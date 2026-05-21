package com.mediamanager.plugin.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LibraryPluginConfigService {

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
                    row.put("pluginId", e.getExtractorType().toLowerCase());
                    row.put("kind", PluginKind.EXTRACTOR.name());
                    row.put("enabled", e.getEnabled() != null ? e.getEnabled() : true);
                    row.put("priority", e.getPriority() != null ? e.getPriority() : 0);
                    row.put("config", e.getConfig() != null ? e.getConfig() : "");
                    return row;
                })
                .toList();
        if (configs.isEmpty()) {
            return;
        }
        replaceConfigs(libraryId, configs);
    }

    @Transactional
    public void replaceConfigs(Integer libraryId, List<Map<String, Object>> configs) {
        libraryAccessService.assertCanViewLibrary(libraryId);
        MediaLibrary library = libraryRepository.findById(libraryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));

        List<LibraryPluginConfig> existing = repository.findByLibrary_IdOrderByPriorityAsc(libraryId);
        repository.deleteAll(existing);

        List<LibraryPluginConfig> toSave = new ArrayList<>();
        for (Map<String, Object> cfg : configs) {
            toSave.add(LibraryPluginConfig.builder()
                    .library(library)
                    .pluginId(String.valueOf(cfg.get("pluginId")))
                    .kind(String.valueOf(cfg.getOrDefault("kind", PluginKind.EXTRACTOR.name())))
                    .enabled(cfg.get("enabled") == null || Boolean.parseBoolean(String.valueOf(cfg.get("enabled"))))
                    .priority(cfg.get("priority") != null ? Integer.parseInt(String.valueOf(cfg.get("priority"))) : 100)
                    .config(cfg.get("config") != null ? String.valueOf(cfg.get("config")) : null)
                    .build());
        }
        repository.saveAll(toSave);
    }

    private Map<String, Object> toMap(LibraryPluginConfig c) {
        return Map.of(
                "pluginId", c.getPluginId(),
                "kind", c.getKind(),
                "enabled", c.getEnabled(),
                "priority", c.getPriority(),
                "config", c.getConfig() != null ? c.getConfig() : "");
    }
}
