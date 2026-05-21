package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scrapes metadata from StashDB / ThePornDB via GraphQL API.
 *
 * Config JSON (in LibraryExtractorConfig.config):
 * {
 *   "endpoint": "https://theporndb.net/graphql",
 *   "apiKey": "your-api-key"
 * }
 *
 * Alternatively use StashDB:
 * {
 *   "endpoint": "https://stashdb.org/graphql",
 *   "apiKey": "your-stashdb-api-key"
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StashDbExtractor implements MetadataExtractor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_ENDPOINT = "https://theporndb.net/graphql";

    // GraphQL query to search scenes
    private static final String SEARCH_SCENE_QUERY = """
            query SearchScenes($term: String!) {
              searchScene(term: $term, filter: { per_page: 5 }) {
                scenes {
                  id
                  title
                  details
                  date
                  urls { url type }
                  images { id url width height }
                  studio { id name }
                  performers {
                    performer { id name disambiguation gender }
                    as
                  }
                  tags { id name }
                  duration
                }
              }
            }
            """;

    // GraphQL query to get scene by ID
    private static final String FIND_SCENE_QUERY = """
            query FindScene($id: ID!) {
              findScene(id: $id) {
                id
                title
                details
                date
                urls { url type }
                images { id url width height }
                studio { id name }
                performers {
                  performer { id name disambiguation gender }
                  as
                }
                tags { id name }
                duration
                fingerprints { hash algorithm duration submissions }
              }
            }
            """;

    @Override
    public String getType() {
        return "STASHDB";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryExtractorConfig config) {
        if (context.primaryFile() == null) return null;

        String endpoint = extractConfigValue(config.getConfig(), "endpoint", DEFAULT_ENDPOINT);
        String apiKey = extractConfigValue(config.getConfig(), "apiKey", null);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("StashDB/PornDB API key not configured for library {}",
                    config.getLibrary().getId());
            return null;
        }

        // Build search query from accumulated title or filename
        String searchQuery = Optional.ofNullable(context.currentAccumulatedResult().getTitle())
                .orElse(context.primaryFile().getFileName());

        // Clean up search query (remove extensions, common markers)
        searchQuery = cleanSearchQuery(searchQuery);

        try {
            // Step 1: Search for scenes
            JsonNode searchResult = executeGraphQL(endpoint, apiKey, SEARCH_SCENE_QUERY,
                    Map.of("term", searchQuery));

            JsonNode scenes = searchResult.path("data").path("searchScene").path("scenes");
            if (!scenes.isArray() || scenes.isEmpty()) {
                log.debug("No StashDB results for query: {}", searchQuery);
                return null;
            }

            // Use first match
            JsonNode scene = scenes.get(0);
            return parseSceneNode(scene, endpoint);

        } catch (Exception e) {
            log.error("Failed to fetch metadata from StashDB for query {}: {}",
                    searchQuery, e.getMessage());
            return null;
        }
    }

    private MetadataResult parseSceneNode(JsonNode scene, String endpoint) {
        MetadataResult result = MetadataResult.builder().build();

        // Title
        result.setTitle(scene.path("title").asText(null));

        // Overview/details
        String details = scene.path("details").asText(null);
        if (details != null && !details.isEmpty()) {
            result.setOverview(details);
        }

        // Date
        String dateStr = scene.path("date").asText(null);
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                result.setReleaseDate(LocalDate.parse(dateStr));
            } catch (Exception ignored) {}
        }

        // Duration (in seconds → minutes)
        int duration = scene.path("duration").asInt(0);
        if (duration > 0) {
            result.setRuntimeMinutes((int) Math.round(duration / 60.0));
        }

        // Cover/poster image (first image)
        JsonNode images = scene.path("images");
        if (images.isArray() && !images.isEmpty()) {
            String imageUrl = images.get(0).path("url").asText(null);
            if (imageUrl != null) {
                result.setPosterPath(imageUrl);
            }
        }

        // Studio
        JsonNode studio = scene.path("studio");
        if (!studio.isMissingNode() && studio.has("name")) {
            result.setStudios(List.of(studio.path("name").asText()));
        }

        // Tags → genres
        JsonNode tags = scene.path("tags");
        if (tags.isArray()) {
            List<String> genres = new ArrayList<>();
            for (JsonNode tag : tags) {
                genres.add(tag.path("name").asText());
            }
            if (!genres.isEmpty()) {
                result.setGenres(genres);
            }
        }

        // Performers → cast
        JsonNode performers = scene.path("performers");
        if (performers.isArray()) {
            List<Map<String, String>> castList = new ArrayList<>();
            for (JsonNode perf : performers) {
                JsonNode performer = perf.path("performer");
                String name = performer.path("name").asText("");
                String role = perf.path("as").asText("");
                if (!name.isEmpty()) {
                    if (!role.isEmpty()) {
                        castList.add(Map.of("name", name, "role", role));
                    } else {
                        castList.add(Map.of("name", name));
                    }
                }
            }
            if (!castList.isEmpty()) {
                try {
                    result.setCastInfo(objectMapper.writeValueAsString(castList));
                } catch (JsonProcessingException ignored) {}
            }
        }

        // Provider IDs
        String sceneId = scene.path("id").asText(null);
        if (sceneId != null) {
            // Determine provider key based on endpoint
            String providerKey = endpoint.contains("stashdb") ? "stashdb" : "porndb";
            result.getProviderIds().put(providerKey, sceneId);
        }

        log.info("StashDB scraped metadata: title={}", result.getTitle());
        return result;
    }

    private JsonNode executeGraphQL(String endpoint, String apiKey, String query,
                                     Map<String, Object> variables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("ApiKey", apiKey);
        headers.set("User-Agent", "MediaManager/1.0");

        Map<String, Object> body = Map.of(
                "query", query,
                "variables", variables
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(endpoint, request, JsonNode.class);

        return response.getBody();
    }

    private String cleanSearchQuery(String query) {
        if (query == null) return "";
        // Remove file extension
        int dotIdx = query.lastIndexOf('.');
        if (dotIdx > 0) {
            query = query.substring(0, dotIdx);
        }
        // Remove common markers
        return query.replaceAll("\\[.*?]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\b(1080p|720p|480p|4k|2160p|WEB-DL|BluRay|HEVC|x264|x265|AAC)\\b", "")
                .replaceAll("[._]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractConfigValue(String configJson, String key, String defaultValue) {
        if (configJson == null || configJson.isEmpty()) return defaultValue;
        try {
            JsonNode node = objectMapper.readTree(configJson);
            String value = node.path(key).asText(null);
            return value != null && !value.isEmpty() ? value : defaultValue;
        } catch (JsonProcessingException e) {
            return defaultValue;
        }
    }
}
