package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbExtractor implements MetadataExtractor {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    @Override
    public String getType() {
        return "TMDB";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryExtractorConfig config) {
        if (context.primaryFile() == null) return null;
        String apiKey = extractApiKey(config.getConfig());
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("TMDb API key not configured for library {}", config.getLibrary().getId());
            return null;
        }

        String searchType = context.mediaItem().getType().equals("TV_SHOW") ? "tv" : "movie";
        
        // Use accumulated title or original filename to search
        String searchQuery = Optional.ofNullable(context.currentAccumulatedResult().getTitle())
                .orElse(context.primaryFile().getFileName());

        try {
            // Search Step
            String searchUrl = UriComponentsBuilder.fromHttpUrl(TMDB_BASE_URL)
                    .path("/search/" + searchType)
                    .queryParam("api_key", apiKey)
                    .queryParam("query", searchQuery)
                    .queryParam("language", context.mediaItem().getLibrary().getLanguage())
                    .build().toUriString();

            String responseFormat = restTemplate.getForObject(searchUrl, String.class);
            JsonNode root = objectMapper.readTree(responseFormat);
            JsonNode results = root.path("results");

            if (results.isArray() && !results.isEmpty()) {
                JsonNode firstMatch = results.get(0);
                String id = firstMatch.get("id").asText();

                // Detailed Fetch Step
                String detailUrl = UriComponentsBuilder.fromHttpUrl(TMDB_BASE_URL)
                        .path("/" + searchType + "/" + id)
                        .queryParam("api_key", apiKey)
                        .queryParam("language", context.mediaItem().getLibrary().getLanguage())
                        .build().toUriString();

                String detailResponse = restTemplate.getForObject(detailUrl, String.class);
                JsonNode detailNode = objectMapper.readTree(detailResponse);

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
                if (poster != null) result.setPosterPath(IMAGE_BASE_URL + poster);
                
                String backdrop = detailNode.path("backdrop_path").asText(null);
                if (backdrop != null) result.setBackdropPath(IMAGE_BASE_URL + backdrop);

                // Genres
                JsonNode genresArr = detailNode.path("genres");
                if (genresArr.isArray()) {
                    List<String> genres = new ArrayList<>();
                    for (JsonNode g : genresArr) genres.add(g.path("name").asText());
                    result.setGenres(genres);
                }

                result.getProviderIds().put("tmdb", id);
                if (detailNode.has("imdb_id")) {
                    result.getProviderIds().put("imdb", detailNode.path("imdb_id").asText());
                }

                log.info("Successfully fetched TMDb metadata for item {}: {}", context.mediaItem().getId(), result.getTitle());
                return result;
            } else {
                log.debug("No TMDB results found for query: {}", searchQuery);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to fetch metadata from TMDb for query {}", searchQuery, e);
            return null;
        }
    }

    private String extractApiKey(String configJson) {
        if (configJson == null || configJson.isEmpty()) return null;
        try {
            JsonNode node = objectMapper.readTree(configJson);
            return node.path("apiKey").asText(null);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
