package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.metadata.service.TmdbClientService;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.spi.MetadataScraper;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbScraper implements MetadataScraper {

    private final TmdbClientService tmdbClientService;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "TMDB";
    }

    @Override
    public MetadataResult scrape(ScrapeContext context, LibraryPluginConfig config) {
        if (context.primaryFile() == null) return null;
        String apiKey = extractApiKey(config.getConfig());
        if (apiKey == null || apiKey.isEmpty()) {
            // Fallback to global sys_config
            try {
                apiKey = tmdbClientService.resolveApiKeyForLibrary(config.getLibrary());
            } catch (Exception e) {
                log.warn("TMDb API key not configured for library {}", config.getLibrary().getId());
                return null;
            }
        }

        String searchType = context.mediaItem().getType();
        String searchQuery = Optional.ofNullable(context.currentAccumulatedResult().getTitle())
                .orElse(context.primaryFile().getFileName());

        try {
            List<Map<String, Object>> candidates = tmdbClientService.search(
                    apiKey,
                    searchType,
                    config.getLibrary().getLanguage(),
                    searchQuery
            );
            if (candidates.isEmpty()) {
                log.debug("No TMDB results found for query: {}", searchQuery);
                return null;
            }
            String externalId = String.valueOf(candidates.get(0).get("id"));
            return tmdbClientService.fetchByExternalId(
                    apiKey,
                    searchType,
                    config.getLibrary().getLanguage(),
                    externalId
            );
        } catch (Exception e) {
            log.error("Failed to fetch metadata from TMDb for query {}", searchQuery, e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> searchCandidates(String query, LibraryPluginConfig config, String mediaType, String language) {
        try {
            String apiKey = tmdbClientService.resolveApiKeyForLibrary(config.getLibrary());
            return tmdbClientService.search(apiKey, mediaType, language, query);
        } catch (Exception e) {
            log.warn("TMDb candidate search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public MetadataResult fetchByExternalId(String externalId, LibraryPluginConfig config, String mediaType, String language) {
        try {
            String apiKey = tmdbClientService.resolveApiKeyForLibrary(config.getLibrary());
            return tmdbClientService.fetchByExternalId(apiKey, mediaType, language, externalId);
        } catch (Exception e) {
            log.error("TMDb fetch by ID failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractApiKey(String configJson) {
        if (configJson == null || configJson.isEmpty()) return null;
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node.has("api_key")) return node.path("api_key").asText(null);
            if (node.has("apiKey")) return node.path("apiKey").asText(null);
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
