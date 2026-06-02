package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.spi.MetadataScraper;
import com.mediamanager.metadata.util.FileNameParser;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavBusScraper implements MetadataScraper {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_BASE_URL = "https://www.javbus.com";

    // HTML parsing patterns (JavBus page structure)
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<h3>([^<]+)</h3>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COVER_PATTERN = Pattern.compile(
            "<a[^>]*class=\"bigImage\"[^>]*href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "<span\\s+class=\"header\">發行日期:</span>\\s*<p>([\\d-]+)</p>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "<span\\s+class=\"header\">長度:</span>\\s*<p>(\\d+)分鐘</p>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STUDIO_PATTERN = Pattern.compile(
            "<span\\s+class=\"header\">製作商:</span>\\s*<p>\\s*<a[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "<span\\s+class=\"header\">發行商:</span>\\s*<p>\\s*<a[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern GENRE_PATTERN = Pattern.compile(
            "<span\\s+class=\"genre\">\\s*<label>\\s*<a[^>]*>([^<]+)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTOR_PATTERN = Pattern.compile(
            "<a[^>]*class=\"avatar-box\"[^>]*>\\s*(?:<div[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"[^>]*/?>\\s*</div>)?\\s*<span>([^<]+)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String getType() {
        return "JAVBUS";
    }

    @Override
    public MetadataResult scrape(ScrapeContext context, LibraryPluginConfig config) {
        if (context.primaryFile() == null) return null;

        String code = extractCode(context);
        if (code == null) {
            log.debug("No JAV code found for item {}", context.mediaItem().getId());
            return null;
        }
        return fetchByCode(code, config.getConfig());
    }

    @Override
    public List<Map<String, Object>> searchCandidates(String query, LibraryPluginConfig config, String mediaType, String language) {
        return searchCandidates(query, config.getConfig());
    }

    @Override
    public MetadataResult fetchByExternalId(String externalId, LibraryPluginConfig config, String mediaType, String language) {
        return fetchByCode(externalId, config.getConfig());
    }

    public List<Map<String, Object>> searchCandidates(String query, String configJson) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String code = query.trim().toUpperCase();
        MetadataResult result = fetchByCode(code, configJson);
        if (result == null) {
            return List.of();
        }
        Map<String, Object> row = new HashMap<>();
        row.put("externalId", code);
        row.put("title", result.getTitle() != null ? result.getTitle() : code);
        row.put("releaseDate", result.getReleaseDate() != null ? result.getReleaseDate().toString() : "");
        row.put("provider", "javbus");
        return List.of(row);
    }

    public MetadataResult fetchByCode(String code, String configJson) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String baseUrl = extractBaseUrl(configJson);
        String url = baseUrl + "/" + code;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String html = response.getBody();
            if (html == null || html.isEmpty()) {
                log.debug("Empty response from JavBus for code {}", code);
                return null;
            }

            MetadataResult result = MetadataResult.builder().build();
            result.getProviderIds().put("javbus", code);

            // Parse title
            Matcher titleMatcher = TITLE_PATTERN.matcher(html);
            if (titleMatcher.find()) {
                String fullTitle = titleMatcher.group(1).trim();
                result.setTitle(fullTitle);
                if (fullTitle.toUpperCase().startsWith(code.toUpperCase())) {
                    String remainder = fullTitle.substring(code.length()).trim();
                    if (!remainder.isEmpty()) {
                        result.setOriginalTitle(remainder);
                    }
                }
            }

            // Parse cover image
            Matcher coverMatcher = COVER_PATTERN.matcher(html);
            if (coverMatcher.find()) {
                result.setPosterPath(coverMatcher.group(1));
            }

            // Parse release date
            Matcher dateMatcher = DATE_PATTERN.matcher(html);
            if (dateMatcher.find()) {
                try {
                    result.setReleaseDate(LocalDate.parse(dateMatcher.group(1)));
                } catch (Exception ignored) {}
            }

            // Parse duration
            Matcher durationMatcher = DURATION_PATTERN.matcher(html);
            if (durationMatcher.find()) {
                result.setRuntimeMinutes(Integer.parseInt(durationMatcher.group(1)));
            }

            // Parse studio
            List<String> studios = new ArrayList<>();
            Matcher studioMatcher = STUDIO_PATTERN.matcher(html);
            if (studioMatcher.find()) {
                studios.add(studioMatcher.group(1).trim());
            }
            Matcher labelMatcher = LABEL_PATTERN.matcher(html);
            if (labelMatcher.find()) {
                String label = labelMatcher.group(1).trim();
                if (!studios.contains(label)) studios.add(label);
            }
            if (!studios.isEmpty()) result.setStudios(studios);

            // Parse genres
            List<String> genres = new ArrayList<>();
            Matcher genreMatcher = GENRE_PATTERN.matcher(html);
            while (genreMatcher.find()) {
                genres.add(genreMatcher.group(1).trim());
            }
            if (!genres.isEmpty()) result.setGenres(genres);

            // Parse actors
            List<Map<String, String>> castList = new ArrayList<>();
            Matcher actorMatcher = ACTOR_PATTERN.matcher(html);
            while (actorMatcher.find()) {
                String thumb = actorMatcher.group(1);
                String name = actorMatcher.group(2).trim();
                if (thumb != null) {
                    castList.add(Map.of("name", name, "thumb", thumb));
                } else {
                    castList.add(Map.of("name", name));
                }
            }
            if (!castList.isEmpty()) {
                try {
                    result.setCastInfo(objectMapper.writeValueAsString(castList));
                } catch (JsonProcessingException ignored) {}
            }

            log.info("JavBus scraped metadata for code {}: title={}", code, result.getTitle());
            return result;

        } catch (Exception e) {
            log.error("Failed to scrape JavBus for code {}: {}", code, e.getMessage());
            return null;
        }
    }

    private String extractCode(ScrapeContext context) {
        MetadataResult accumulated = context.currentAccumulatedResult();
        if (accumulated != null && accumulated.getProviderIds() != null) {
            String code = accumulated.getProviderIds().get("javbus");
            if (code != null && !code.isEmpty()) return code;
            code = accumulated.getProviderIds().get("jav_code");
            if (code != null && !code.isEmpty()) return code;
        }

        String fileName = context.primaryFile().getFileName();
        return FileNameParser.extractJavCode(fileName);
    }

    private String extractBaseUrl(String configJson) {
        if (configJson == null || configJson.isEmpty()) return DEFAULT_BASE_URL;
        try {
            JsonNode node = objectMapper.readTree(configJson);
            String url = node.path("baseUrl").asText(null);
            return url != null && !url.isEmpty() ? url : DEFAULT_BASE_URL;
        } catch (JsonProcessingException e) {
            return DEFAULT_BASE_URL;
        }
    }
}
