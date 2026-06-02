package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Example extractor for plugin registry demos. Enable per library with type {@code MOCK}
 * and JSON config {@code {"mockTitle":"My Title"}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockMetadataExtractor implements MetadataExtractor {

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "MOCK";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryPluginConfig config) {
        if (config == null || config.getConfig() == null || config.getConfig().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(config.getConfig());
            String mockTitle = node.path("mockTitle").asText(null);
            if (mockTitle == null || mockTitle.isBlank()) {
                return null;
            }
            return MetadataResult.builder()
                    .title(mockTitle)
                    .overview("Mock extractor (P2-6 example plugin)")
                    .build();
        } catch (Exception e) {
            log.warn("Invalid MOCK extractor config: {}", e.getMessage());
            return null;
        }
    }
}
