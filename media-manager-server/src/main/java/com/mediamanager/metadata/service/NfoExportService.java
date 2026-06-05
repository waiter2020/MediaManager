package com.mediamanager.metadata.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NfoExportService {

    private final MediaFileRepository fileRepository;
    private final MovieMetadataRepository movieMetadataRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void export(MediaItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        if (!"MOVIE".equals(item.getType()) && !"TV_SHOW".equals(item.getType())) {
            return; // Only export movies and TV shows
        }

        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        if (files.isEmpty()) {
            return;
        }
        MediaFile primaryFile = files.get(0);
        if (primaryFile.getFilePath() == null || primaryFile.getFilePath().isBlank()) {
            return;
        }

        try {
            Path videoPath = Paths.get(primaryFile.getFilePath());
            Path parent = videoPath.getParent();
            if (parent == null || !Files.exists(parent)) {
                log.warn("NFO directory does not exist for file path: {}", primaryFile.getFilePath());
                return;
            }

            String fileName = videoPath.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String baseName = dot == -1 ? fileName : fileName.substring(0, dot);
            Path nfoPath = parent.resolve(baseName + ".nfo");

            String xml = buildNfoXml(item);
            Files.writeString(nfoPath, xml, StandardCharsets.UTF_8);
            log.info("Successfully exported NFO metadata to {}", nfoPath);
        } catch (Exception e) {
            log.error("Failed to export NFO metadata for item {}", item.getId(), e);
        }
    }

    private String buildNfoXml(MediaItem item) {
        String rootTag = "MOVIE".equals(item.getType()) ? "movie" : "tvshow";
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<").append(rootTag).append(">\n");

        appendTag(sb, "title", item.getTitle());
        appendTag(sb, "originaltitle", item.getOriginalTitle());
        appendTag(sb, "plot", item.getOverview());

        if (item.getRating() != null) {
            appendTag(sb, "rating", item.getRating().toString());
        }
        if (item.getReleaseDate() != null) {
            appendTag(sb, "releasedate", item.getReleaseDate().toString());
            appendTag(sb, "year", String.valueOf(item.getReleaseDate().getYear()));
        }

        // Add provider IDs
        if (item.getProviderIds() != null && !item.getProviderIds().isBlank()) {
            try {
                Map<String, String> providers = objectMapper.readValue(item.getProviderIds(), new TypeReference<>() {});
                if (providers != null) {
                    providers.forEach((provider, id) -> {
                        if (id != null && !id.isBlank()) {
                            sb.append("  <uniqueid default=\"")
                              .append("tmdb".equals(provider) ? "true" : "false")
                              .append("\" type=\"")
                              .append(escapeXml(provider))
                              .append("\">")
                              .append(escapeXml(id))
                              .append("</uniqueid>\n");
                        }
                    });
                }
            } catch (Exception e) {
                log.debug("Failed to parse provider IDs for NFO generation", e);
            }
        }

        // Add tags
        if (item.getTags() != null) {
            item.getTags().forEach(tag -> appendTag(sb, "tag", tag.getName()));
        }

        // Add categories as genres
        if (item.getCategories() != null) {
            item.getCategories().forEach(cat -> appendTag(sb, "genre", cat.getName()));
        }

        // Add movieMetadata-specific tags (tagline, actors, studios)
        if ("MOVIE".equals(item.getType()) && item.getId() != null) {
            Optional<MovieMetadata> mmOpt = movieMetadataRepository.findByMediaItemId(item.getId());
            if (mmOpt.isPresent()) {
                MovieMetadata mm = mmOpt.get();

                // Tagline
                if (mm.getTagline() != null && !mm.getTagline().isBlank()) {
                    appendTag(sb, "tagline", mm.getTagline());
                }

                // Actors from castInfo JSON
                if (mm.getCastInfo() != null && !mm.getCastInfo().isBlank()) {
                    try {
                        List<Map<String, String>> castList = objectMapper.readValue(
                                mm.getCastInfo(), new TypeReference<List<Map<String, String>>>() {});
                        for (Map<String, String> actor : castList) {
                            sb.append("  <actor>\n");
                            appendTag(sb, "    name", actor.get("name"));
                            String role = actor.getOrDefault("character", actor.get("role"));
                            appendTag(sb, "    role", role);
                            String profilePath = actor.get("profile_path");
                            if (profilePath != null && !profilePath.isBlank()) {
                                String thumbUrl = profilePath.startsWith("http")
                                        ? profilePath
                                        : "https://image.tmdb.org/t/p/w185" + profilePath;
                                appendTag(sb, "    thumb", thumbUrl);
                            }
                            sb.append("  </actor>\n");
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse castInfo for NFO generation", e);
                    }
                }

                // Studios from studios JSON
                if (mm.getStudios() != null && !mm.getStudios().isBlank()) {
                    try {
                        List<String> studioList = objectMapper.readValue(
                                mm.getStudios(), new TypeReference<List<String>>() {});
                        for (String studio : studioList) {
                            if (studio != null && !studio.isBlank()) {
                                appendTag(sb, "studio", studio);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse studios for NFO generation", e);
                    }
                }
            }
        }

        sb.append("</").append(rootTag).append(">\n");
        return sb.toString();
    }

    private void appendTag(StringBuilder sb, String tag, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("  <").append(tag).append(">")
              .append(escapeXml(value))
              .append("</").append(tag).append(">\n");
        }
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
