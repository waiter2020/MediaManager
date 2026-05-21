package com.mediamanager.metadata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    public String resolveApiKeyForLibrary(MediaLibrary library) {
        if (library.getExtractorConfigs() == null) {
            throw new BusinessException(ErrorCode.METADATA_EXTRACT_ERROR, "TMDb API key not configured for library");
        }
        return library.getExtractorConfigs().stream()
                .filter(c -> "TMDB".equalsIgnoreCase(c.getExtractorType()) && Boolean.TRUE.equals(c.getEnabled()))
                .map(c -> extractApiKey(c.getConfig()))
                .filter(k -> k != null && !k.isBlank())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.METADATA_EXTRACT_ERROR, "TMDb API key not configured for library"));
    }

    public List<Map<String, Object>> search(String apiKey, String mediaType, String language, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String searchType = "TV_SHOW".equals(mediaType) ? "tv" : "movie";
        try {
            String searchUrl = UriComponentsBuilder.fromHttpUrl(TMDB_BASE_URL)
                    .path("/search/" + searchType)
                    .queryParam("api_key", apiKey)
                    .queryParam("query", query)
                    .queryParam("language", language != null ? language : "zh-CN")
                    .build()
                    .toUriString();
            String response = restTemplate.getForObject(searchUrl, String.class);
            JsonNode root = objectMapper.readTree(response);
            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode node : root.path("results")) {
                if (results.size() >= 12) {
                    break;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("id", node.path("id").asText());
                row.put("title", node.path(searchType.equals("movie") ? "title" : "name").asText(null));
                row.put("overview", node.path("overview").asText(null));
                row.put("releaseDate", node.path(searchType.equals("movie") ? "release_date" : "first_air_date").asText(null));
                row.put("posterUrl", node.has("poster_path") && !node.path("poster_path").isNull()
                        ? IMAGE_BASE_URL + node.path("poster_path").asText()
                        : null);
                results.add(row);
            }
            return results;
        } catch (Exception e) {
            log.error("TMDb search failed for {}", query, e);
            throw new BusinessException(ErrorCode.METADATA_EXTRACT_ERROR, "TMDb search failed: " + e.getMessage());
        }
    }

    public MetadataResult fetchByExternalId(String apiKey, String mediaType, String language, String externalId) {
        String searchType = "TV_SHOW".equals(mediaType) ? "tv" : "movie";
        try {
            String detailUrl = UriComponentsBuilder.fromHttpUrl(TMDB_BASE_URL)
                    .path("/" + searchType + "/" + externalId)
                    .queryParam("api_key", apiKey)
                    .queryParam("language", language != null ? language : "zh-CN")
                    .build()
                    .toUriString();

            String detailResponse = restTemplate.getForObject(detailUrl, String.class);
            JsonNode detailNode = objectMapper.readTree(detailResponse);
            return mapDetailNode(detailNode, searchType, externalId);
        } catch (Exception e) {
            log.error("TMDb fetch failed for id {}", externalId, e);
            throw new BusinessException(ErrorCode.METADATA_EXTRACT_ERROR, "TMDb fetch failed: " + e.getMessage());
        }
    }

    private MetadataResult mapDetailNode(JsonNode detailNode, String searchType, String externalId) {
        MetadataResult result = MetadataResult.builder().build();
        result.setTitle(detailNode.path(searchType.equals("movie") ? "title" : "name").asText(null));
        result.setOriginalTitle(detailNode.path(searchType.equals("movie") ? "original_title" : "original_name").asText(null));
        result.setOverview(detailNode.path("overview").asText(null));

        String releaseDateStr = detailNode.path(searchType.equals("movie") ? "release_date" : "first_air_date").asText(null);
        if (releaseDateStr != null && !releaseDateStr.isEmpty()) {
            result.setReleaseDate(LocalDate.parse(releaseDateStr));
        }

        double voteAverage = detailNode.path("vote_average").asDouble(0.0);
        result.setRating(voteAverage > 0 ? voteAverage : null);

        String poster = detailNode.path("poster_path").asText(null);
        if (poster != null) {
            result.setPosterPath(IMAGE_BASE_URL + poster);
        }
        String backdrop = detailNode.path("backdrop_path").asText(null);
        if (backdrop != null) {
            result.setBackdropPath(IMAGE_BASE_URL + backdrop);
        }

        JsonNode genresArr = detailNode.path("genres");
        if (genresArr.isArray()) {
            List<String> genres = new ArrayList<>();
            for (JsonNode g : genresArr) {
                genres.add(g.path("name").asText());
            }
            result.setGenres(genres);
        }

        result.getProviderIds().put("tmdb", externalId);
        if (detailNode.has("imdb_id")) {
            result.getProviderIds().put("imdb", detailNode.path("imdb_id").asText());
        }
        return result;
    }

    private String extractApiKey(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node.has("apiKey")) {
                return node.path("apiKey").asText(null);
            }
            if (node.has("api_key")) {
                return node.path("api_key").asText(null);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
