package com.mediamanager.metadata.service;

import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetadataPipelineService {

    private static final Set<String> LOCAL_EXTRACTORS = Set.of("NFO", "FFPROBE", "EXIF");
    private static final Set<String> REMOTE_EXTRACTORS = Set.of("TMDB", "JAVBUS", "STASHDB");

    private final Map<String, MetadataExtractor> extractorsByType;
    private final LibraryPluginConfigRepository pluginConfigRepository;

    public MetadataPipelineService(
            List<MetadataExtractor> extractors,
            LibraryPluginConfigRepository pluginConfigRepository) {
        this.extractorsByType = extractors.stream()
                .collect(Collectors.toMap(e -> e.getType().toUpperCase(), Function.identity(), (a, b) -> a));
        this.pluginConfigRepository = pluginConfigRepository;
        log.info("Registered metadata extractors: {}", extractorsByType.keySet());
    }

    /** Scan / refresh: local extractors only (no network scrapers). */
    public MetadataResult executeLocalPipeline(MediaItem item, MediaFile primaryFile) {
        return executePipeline(item, primaryFile, true);
    }

    /** Scrape tasks: remote scrapers + local extractors. */
    public MetadataResult executeScrapePipeline(MediaItem item, MediaFile primaryFile) {
        return executePipeline(item, primaryFile, false);
    }

    /** @deprecated use executeLocalPipeline or executeScrapePipeline */
    public MetadataResult executePipeline(MediaItem item, MediaFile primaryFile) {
        return executeLocalPipeline(item, primaryFile);
    }

    private MetadataResult executePipeline(MediaItem item, MediaFile primaryFile, boolean localOnly) {
        MediaLibrary library = item.getLibrary();
        List<PipelineConfig> configs = resolveConfigs(library);

        MetadataResult accumulatedResult = MetadataResult.builder().build();

        for (PipelineConfig config : configs) {
            String type = config.extractorType().toUpperCase();
            if (localOnly && !LOCAL_EXTRACTORS.contains(type)) {
                continue;
            }
            if (!localOnly && !LOCAL_EXTRACTORS.contains(type) && !REMOTE_EXTRACTORS.contains(type)) {
                continue;
            }

            MetadataExtractor extractor = extractorsByType.get(type);
            if (extractor == null) {
                log.warn("Configured extractor {} not found in registry", type);
                continue;
            }
            try {
                log.debug("Running extractor {} for item {} (localOnly={})", type, item.getId(), localOnly);
                MetadataExtractor.ExtractorContext context =
                        new MetadataExtractor.ExtractorContext(item, primaryFile, accumulatedResult);
                LibraryExtractorConfig legacyConfig = LibraryExtractorConfig.builder()
                        .extractorType(type)
                        .enabled(true)
                        .priority(config.priority())
                        .config(config.configJson())
                        .library(library)
                        .build();
                MetadataResult partialResult = extractor.extract(context, legacyConfig);
                if (partialResult != null) {
                    accumulatedResult.mergeFrom(partialResult);
                }
            } catch (Exception e) {
                log.error("Extractor {} failed for item {}", type, item.getId(), e);
            }
        }

        return accumulatedResult;
    }

    private List<PipelineConfig> resolveConfigs(MediaLibrary library) {
        List<LibraryPluginConfig> pluginConfigs = pluginConfigRepository
                .findByLibrary_IdOrderByPriorityAsc(library.getId()).stream()
                .filter(c -> PluginKind.EXTRACTOR.name().equals(c.getKind()) || "SCRAPER".equals(c.getKind()))
                .filter(LibraryPluginConfig::getEnabled)
                .toList();

        if (!pluginConfigs.isEmpty()) {
            return pluginConfigs.stream()
                    .map(c -> new PipelineConfig(c.getPluginId().toUpperCase(), c.getPriority(), c.getConfig()))
                    .toList();
        }

        return library.getExtractorConfigs().stream()
                .filter(LibraryExtractorConfig::getEnabled)
                .sorted(Comparator.comparingInt(LibraryExtractorConfig::getPriority))
                .map(c -> new PipelineConfig(c.getExtractorType().toUpperCase(), c.getPriority(), c.getConfig()))
                .toList();
    }

    private record PipelineConfig(String extractorType, int priority, String configJson) {}
}
