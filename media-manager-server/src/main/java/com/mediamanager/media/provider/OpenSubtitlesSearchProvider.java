package com.mediamanager.media.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.service.RateLimiterService;
import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.dto.SubtitleSearchResultDto;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.spi.SubtitleSearchProvider;
import com.mediamanager.media.util.OpenSubtitlesMovieHash;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSubtitlesSearchProvider implements SubtitleSearchProvider {

    public static final String PROVIDER_ID = "opensubtitles";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final RateLimiterService rateLimiterService;
    private final StoragePathMapper storagePathMapper;

    private final AtomicReference<TokenCache> tokenCache = new AtomicReference<>();

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isConfigured() {
        return !sysConfigService.opensubtitlesApiKey().isBlank();
    }

    @Override
    public List<SubtitleSearchResultDto> search(SearchContext context) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            return rateLimiterService.executeWithRateLimit("opensubtitles", 2, () -> doSearch(context));
        } catch (Exception e) {
            log.warn("OpenSubtitles search failed: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public byte[] download(String externalId) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(), "OpenSubtitles is not configured");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "externalId is required");
        }
        try {
            return rateLimiterService.executeWithRateLimit("opensubtitles", 2, () -> doDownload(externalId));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(),
                    "Failed to download subtitle: " + e.getMessage());
        }
    }

    private List<SubtitleSearchResultDto> doSearch(SearchContext context) throws IOException {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(sysConfigService.opensubtitlesBaseUrl() + "/subtitles");
        if (context.query() != null && !context.query().isBlank()) {
            builder.queryParam("query", context.query().trim());
        }
        String language = normalizeLanguage(context.language());
        if (!language.isBlank()) {
            builder.queryParam("languages", language);
        }
        String tmdbId = extractTmdbId(context.mediaItem().getProviderIds());
        if (tmdbId != null) {
            builder.queryParam("tmdb_id", tmdbId);
        }
        MediaFile primaryFile = context.primaryFile();
        if (primaryFile != null && primaryFile.getFilePath() != null) {
            Path mediaPath = Path.of(storagePathMapper.mapPathIfNeeded(primaryFile.getFilePath()));
            if (Files.isRegularFile(mediaPath)) {
                long size = Files.size(mediaPath);
                builder.queryParam("moviebytesize", size);
                String hash = OpenSubtitlesMovieHash.compute(mediaPath);
                if (hash != null) {
                    builder.queryParam("moviehash", hash);
                }
            }
        }

        HttpHeaders headers = baseHeaders();
        ResponseEntity<String> response = restTemplate.exchange(
                builder.build(true).toUri(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        return parseSearchResponse(response.getBody());
    }

    private byte[] doDownload(String externalId) throws IOException {
        String token = resolveAuthToken();
        HttpHeaders headers = baseHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = objectMapper.writeValueAsString(Map.of("file_id", Integer.parseInt(externalId)));
        ResponseEntity<String> response = restTemplate.exchange(
                sysConfigService.opensubtitlesBaseUrl() + "/download",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        String link = root.path("link").asText(null);
        if (link == null || link.isBlank()) {
            link = root.path("data").path("link").asText(null);
        }
        if (link == null || link.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "OpenSubtitles download link missing");
        }
        byte[] payload = restTemplate.getForObject(link, byte[].class);
        if (payload == null || payload.length == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "OpenSubtitles download empty");
        }
        return extractSubtitleBytes(payload);
    }

    private List<SubtitleSearchResultDto> parseSearchResponse(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<SubtitleSearchResultDto> results = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode attributes = item.path("attributes");
            String language = attributes.path("language").asText(null);
            String release = attributes.path("release").asText(null);
            String fileName = attributes.path("files").path(0).path("file_name").asText(null);
            String fileId = attributes.path("files").path(0).path("file_id").asText(null);
            if (fileId == null || fileId.isBlank()) {
                fileId = attributes.path("subtitle_id").asText(item.path("id").asText(null));
            }
            if (fileId == null || fileId.isBlank()) {
                continue;
            }
            int downloadCount = attributes.path("download_count").asInt(0);
            float score = Math.min(100f, 40f + Math.min(downloadCount, 60));
            if (attributes.path("moviehash_match").asBoolean(false)) {
                score += 30f;
            }
            String format = guessFormat(fileName);
            results.add(SubtitleSearchResultDto.builder()
                    .provider(PROVIDER_ID)
                    .externalId(fileId)
                    .title(release != null ? release : fileName)
                    .language(language)
                    .format(format)
                    .releaseName(release)
                    .score(score)
                    .build());
        }
        return results;
    }

    private String resolveAuthToken() throws IOException {
        String username = sysConfigService.opensubtitlesUsername();
        String password = sysConfigService.opensubtitlesPassword();
        if (username.isBlank() || password.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR.getCode(),
                    "OpenSubtitles username/password required for download");
        }
        TokenCache cached = tokenCache.get();
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.token();
        }
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        ResponseEntity<String> response = restTemplate.exchange(
                sysConfigService.opensubtitlesBaseUrl() + "/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        String token = root.path("token").asText(null);
        if (token == null || token.isBlank()) {
            token = root.path("data").path("token").asText(null);
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "OpenSubtitles login failed");
        }
        tokenCache.set(new TokenCache(token, Instant.now().plusSeconds(12 * 3600)));
        return token;
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", sysConfigService.opensubtitlesApiKey());
        headers.set("User-Agent", sysConfigService.subtitleUserAgent());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }
        return language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String extractTmdbId(String providerIdsJson) {
        if (providerIdsJson == null || providerIdsJson.isBlank()) {
            return null;
        }
        String lower = providerIdsJson.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("tmdb");
        if (idx < 0) {
            return null;
        }
        String digits = providerIdsJson.substring(idx).replaceAll("[^0-9]", " ");
        for (String part : digits.trim().split("\\s+")) {
            if (!part.isBlank()) {
                return part;
            }
        }
        return null;
    }

    private static String guessFormat(String fileName) {
        if (fileName == null) {
            return "srt";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ass") || lower.endsWith(".ssa")) {
            return "ass";
        }
        if (lower.endsWith(".vtt")) {
            return "vtt";
        }
        return "srt";
    }

    private static byte[] extractSubtitleBytes(byte[] payload) throws IOException {
        if (looksLikeTextSubtitle(payload)) {
            return payload;
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(payload))) {
            ZipEntry entry;
            byte[] best = null;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".srt") && !name.endsWith(".ass") && !name.endsWith(".ssa") && !name.endsWith(".vtt")) {
                    continue;
                }
                byte[] extracted = readAll(zip);
                if (name.endsWith(".srt")) {
                    return extracted;
                }
                if (best == null) {
                    best = extracted;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return payload;
    }

    private static boolean looksLikeTextSubtitle(byte[] payload) {
        int len = Math.min(payload.length, 32);
        String prefix = new String(payload, 0, len, StandardCharsets.UTF_8).trim();
        return prefix.startsWith("WEBVTT")
                || prefix.matches("\\d+")
                || prefix.startsWith("[Script Info]")
                || prefix.contains("-->");
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private record TokenCache(String token, Instant expiresAt) {
    }
}
