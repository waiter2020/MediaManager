package com.mediamanager.metadata.service;

import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MetadataPipelineService {

    private final Map<String, MetadataExtractor> extractorsByType;

    public MetadataPipelineService(List<MetadataExtractor> extractors) {
        this.extractorsByType = extractors.stream()
                .collect(Collectors.toMap(MetadataExtractor::getType, Function.identity()));
        log.info("Registered metadata extractors: {}", extractorsByType.keySet());
    }

    public MetadataResult executePipeline(MediaItem item, MediaFile primaryFile) {
        MediaLibrary library = item.getLibrary();
        List<LibraryExtractorConfig> configs = library.getExtractorConfigs().stream()
                .filter(LibraryExtractorConfig::getEnabled)
                .sorted(Comparator.comparingInt(LibraryExtractorConfig::getPriority))
                .toList();

        MetadataResult accumulatedResult = MetadataResult.builder().build();

        for (LibraryExtractorConfig config : configs) {
            MetadataExtractor extractor = extractorsByType.get(config.getExtractorType());
            if (extractor != null) {
                try {
                    log.debug("Running extractor {} for item {}", extractor.getType(), item.getId());
                    MetadataExtractor.ExtractorContext context = new MetadataExtractor.ExtractorContext(item, primaryFile, accumulatedResult);
                    MetadataResult partialResult = extractor.extract(context, config);
                    
                    if (partialResult != null) {
                        accumulatedResult.mergeFrom(partialResult);
                    }
                } catch (Exception e) {
                    log.error("Extractor {} failed for item {}", extractor.getType(), item.getId(), e);
                    // Continue pipeline even if one extractor fails
                }
            } else {
                log.warn("Configured extractor {} not found in registry", config.getExtractorType());
            }
        }

        return accumulatedResult;
    }
}
