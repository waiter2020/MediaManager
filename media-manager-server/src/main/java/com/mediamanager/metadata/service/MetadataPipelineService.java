package com.mediamanager.metadata.service;

import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataScraper;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetadataPipelineService {

    private final Map<String, MetadataExtractor> extractorsByType;
    private final Map<String, MetadataScraper> scrapersByType;
    private final LibraryPluginConfigRepository pluginConfigRepository;

    public MetadataPipelineService(
            List<MetadataExtractor> extractors,
            List<MetadataScraper> scrapers,
            LibraryPluginConfigRepository pluginConfigRepository) {
        this.extractorsByType = extractors.stream()
                .collect(Collectors.toMap(e -> e.getType().toUpperCase(), Function.identity(), (a, b) -> a));
        this.scrapersByType = scrapers.stream()
                .collect(Collectors.toMap(s -> s.getType().toUpperCase(), Function.identity(), (a, b) -> a));
        this.pluginConfigRepository = pluginConfigRepository;
        log.info("Registered metadata extractors: {}, scrapers: {}", extractorsByType.keySet(), scrapersByType.keySet());
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
        List<LibraryPluginConfig> pluginConfigs = pluginConfigRepository
                .findByLibrary_IdOrderByPriorityAsc(library.getId()).stream()
                .filter(c -> localOnly
                        ? PluginKind.EXTRACTOR.name().equals(c.getKind())
                        : (PluginKind.EXTRACTOR.name().equals(c.getKind()) || PluginKind.SCRAPER.name().equals(c.getKind())))
                .filter(LibraryPluginConfig::getEnabled)
                .toList();

        MetadataResult accumulatedResult = MetadataResult.builder().build();

        if (!pluginConfigs.isEmpty()) {
            for (LibraryPluginConfig config : pluginConfigs) {
                String type = config.getPluginId().toUpperCase();
                try {
                    log.debug("Running plugin {}/{} for item {} (localOnly={})", config.getKind(), type, item.getId(), localOnly);
                    if (PluginKind.EXTRACTOR.name().equals(config.getKind())) {
                        MetadataExtractor extractor = extractorsByType.get(type);
                        if (extractor == null) {
                            log.warn("Configured extractor {} not found in registry", type);
                            continue;
                        }
                        MetadataExtractor.ExtractorContext context =
                                new MetadataExtractor.ExtractorContext(item, primaryFile, accumulatedResult);
                        MetadataResult partialResult = extractor.extract(context, config);
                        if (partialResult != null) {
                            accumulatedResult.mergeFrom(partialResult);
                        }
                    } else if (PluginKind.SCRAPER.name().equals(config.getKind())) {
                        MetadataScraper scraper = scrapersByType.get(type);
                        if (scraper == null) {
                            log.warn("Configured scraper {} not found in registry", type);
                            continue;
                        }
                        MetadataScraper.ScrapeContext context =
                                new MetadataScraper.ScrapeContext(item, primaryFile, accumulatedResult);
                        MetadataResult partialResult = scraper.scrape(context, config);
                        if (partialResult != null) {
                            accumulatedResult.mergeFrom(partialResult);
                        }
                    }
                } catch (Exception e) {
                    log.error("Plugin {} failed for item {}", type, item.getId(), e);
                }
            }
        } else {
            // Legacy fallback using library.getExtractorConfigs()
            for (LibraryExtractorConfig legacy : library.getExtractorConfigs()) {
                if (legacy.getEnabled() == null || !legacy.getEnabled()) continue;
                String type = legacy.getExtractorType().toUpperCase();
                boolean isScraper = Set.of("TMDB", "JAVBUS", "STASHDB").contains(type);
                if (localOnly && isScraper) continue;

                try {
                    LibraryPluginConfig dummyConfig = LibraryPluginConfig.builder()
                            .library(library)
                            .pluginId(type.toLowerCase())
                            .kind(isScraper ? PluginKind.SCRAPER.name() : PluginKind.EXTRACTOR.name())
                            .enabled(true)
                            .priority(legacy.getPriority())
                            .config(legacy.getConfig())
                            .build();

                    if (!isScraper) {
                        MetadataExtractor extractor = extractorsByType.get(type);
                        if (extractor != null) {
                            MetadataExtractor.ExtractorContext context =
                                    new MetadataExtractor.ExtractorContext(item, primaryFile, accumulatedResult);
                            MetadataResult partialResult = extractor.extract(context, dummyConfig);
                            if (partialResult != null) {
                                accumulatedResult.mergeFrom(partialResult);
                            }
                        }
                    } else {
                        MetadataScraper scraper = scrapersByType.get(type);
                        if (scraper != null) {
                            MetadataScraper.ScrapeContext context =
                                    new MetadataScraper.ScrapeContext(item, primaryFile, accumulatedResult);
                            MetadataResult partialResult = scraper.scrape(context, dummyConfig);
                            if (partialResult != null) {
                                accumulatedResult.mergeFrom(partialResult);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Legacy fallback failed for {} on item {}", type, item.getId(), e);
                }
            }
        }

        return accumulatedResult;
    }
}
