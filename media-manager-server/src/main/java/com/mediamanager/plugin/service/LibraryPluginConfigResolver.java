package com.mediamanager.plugin.service;

import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Unified config lookup: prefer {@code library_plugin_config}, fall back to legacy extractor table.
 */
@Service
@RequiredArgsConstructor
public class LibraryPluginConfigResolver {

    private final LibraryPluginConfigRepository pluginConfigRepository;

    public String resolveConfigJson(MediaLibrary library, String pluginId) {
        if (library == null || pluginId == null) {
            return null;
        }
        List<LibraryPluginConfig> pluginRows = pluginConfigRepository
                .findByLibrary_IdOrderByPriorityAsc(library.getId());
        String fromPlugin = pluginRows.stream()
                .filter(c -> pluginId.equalsIgnoreCase(c.getPluginId()))
                .filter(c -> PluginKind.EXTRACTOR.name().equals(c.getKind()) || "SCRAPER".equals(c.getKind()))
                .filter(LibraryPluginConfig::getEnabled)
                .map(LibraryPluginConfig::getConfig)
                .filter(cfg -> cfg != null && !cfg.isBlank())
                .findFirst()
                .orElse(null);
        if (fromPlugin != null) {
            return fromPlugin;
        }
        if (library.getExtractorConfigs() == null) {
            return null;
        }
        return library.getExtractorConfigs().stream()
                .filter(c -> pluginId.equalsIgnoreCase(c.getExtractorType()) && Boolean.TRUE.equals(c.getEnabled()))
                .map(LibraryExtractorConfig::getConfig)
                .filter(cfg -> cfg != null && !cfg.isBlank())
                .findFirst()
                .orElse(null);
    }
}
