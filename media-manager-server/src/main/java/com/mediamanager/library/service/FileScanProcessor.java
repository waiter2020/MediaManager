package com.mediamanager.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.classification.service.ClassificationEngine;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.ThumbnailService;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.TvShowMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.TvShowMetadataRepository;
import com.mediamanager.metadata.service.MetadataPipelineService;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.util.FileNameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Set;

/**
 * Handles per-file processing in an independent transaction.
 * Separated from LibraryScanService to ensure @Transactional proxy works correctly
 * when called from @Async methods.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileScanProcessor {

    private final MediaItemRepository itemRepository;
    private final MediaFileRepository fileRepository;
    private final MetadataPipelineService pipelineService;
    private final FileNameParser fileNameParser;
    private final MovieMetadataRepository movieMetadataRepository;
    private final TvShowMetadataRepository tvShowMetadataRepository;
    private final ClassificationEngine classificationEngine;
    private final ThumbnailService thumbnailService;
    private final ObjectMapper objectMapper;

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "3gp", "asf", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4",
            "mpg", "mpeg", "mts", "ogv", "ts", "vob", "webm", "wmv"
    );

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "aac", "ac3", "aiff", "alac", "amr", "ape", "flac", "m4a",
            "mp3", "ogg", "opus", "wav", "wma"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "avif", "bmp", "gif", "heic", "heif", "ico", "jfif", "jpg",
            "jpeg", "png", "tif", "tiff", "webp"
    );

    /**
     * Check if this file matches the library type and is not already in database.
     * Returns the determined media type or null if the file should be skipped.
     */
    public String checkFile(MediaLibrary library, Path file) {
        String fileName = file.getFileName().toString();
        String extension = getExtension(fileName).toLowerCase();
        String mediaType = determineMediaType(library.getType(), extension);
        if (mediaType == null) return null;

        String filePath = file.toAbsolutePath().toString();
        if (fileRepository.existsByFilePathAndNotDeleted(filePath)) {
            return null;
        }
        return mediaType;
    }

    /**
     * Process a single file in its own transaction.
     * This ensures that failure of one file does not roll back others.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFile(MediaLibrary library, Path file, BasicFileAttributes attrs, String mediaType) {
        String fileName = file.getFileName().toString();
        String filePath = file.toAbsolutePath().toString();

        log.debug("Found new file: {}", filePath);

        // Create MediaItem
        MediaItem item = MediaItem.builder()
                .library(library)
                .title(fileName)
                .originalTitle(fileName)
                .type(mediaType)
                .status("UNIDENTIFIED")
                .lastScannedAt(Instant.now())
                .build();
        itemRepository.save(item);

        // Create MediaFile
        String extension = getExtension(fileName).toLowerCase();
        MediaFile mediaFile = MediaFile.builder()
                .mediaItem(item)
                .filePath(filePath)
                .fileName(fileName)
                .fileSize(attrs.size())
                .container(extension)
                .fileModifiedAt(attrs.lastModifiedTime().toInstant())
                .deleted(false)
                .build();
        fileRepository.save(mediaFile);

        // Execute Metadata Pipeline
        try {
            MetadataResult pipelineResult = pipelineService.executePipeline(item, mediaFile);

            // If Pipeline returned nothing useful, fallback to FileNameParser
            if (pipelineResult.getTitle() == null) {
                MetadataResult fallback = fileNameParser.parse(fileName);
                pipelineResult.mergeFrom(fallback);
            }

            // Update Item
            item.setTitle(pipelineResult.getTitle());
            item.setOriginalTitle(pipelineResult.getOriginalTitle());
            item.setReleaseDate(pipelineResult.getReleaseDate());
            item.setOverview(pipelineResult.getOverview());
            item.setRating(pipelineResult.getRating() != null ? java.math.BigDecimal.valueOf(pipelineResult.getRating()) : null);
            item.setPosterPath(pipelineResult.getPosterPath());
            item.setBackdropPath(pipelineResult.getBackdropPath());

            // If no poster from metadata, generate thumbnail from video file
            if (item.getPosterPath() == null && ("MOVIE".equals(mediaType) || "TV_SHOW".equals(mediaType))) {
                String thumbnailPath = thumbnailService.generateThumbnail(item.getId(), filePath);
                if (thumbnailPath != null) {
                    item.setPosterPath(thumbnailPath);
                }
            }

            item.setStatus("IDENTIFIED");

            itemRepository.save(item);

            // Persist specific metadata
            if ("MOVIE".equals(item.getType())) {
                MovieMetadata mm = MovieMetadata.builder()
                        .mediaItem(item)
                        .runtimeMinutes(pipelineResult.getRuntimeMinutes())
                        .certification(pipelineResult.getCertification())
                        .genres(pipelineResult.getGenres() != null ? toJson(pipelineResult.getGenres()) : null)
                        .castInfo(pipelineResult.getCastInfo())
                        .studios(pipelineResult.getStudios() != null ? toJson(pipelineResult.getStudios()) : null)
                        .build();
                movieMetadataRepository.save(mm);
            } else if ("TV_SHOW".equals(item.getType())) {
                TvShowMetadata tm = TvShowMetadata.builder()
                        .mediaItem(item)
                        .network(pipelineResult.getNetwork())
                        .status(pipelineResult.getStatus())
                        .genres(pipelineResult.getGenres() != null ? toJson(pipelineResult.getGenres()) : null)
                        .castInfo(pipelineResult.getCastInfo())
                        .build();
                tvShowMetadataRepository.save(tm);
            }

            // Execute Classification Engine
            classificationEngine.executeClassification(item);

            log.info("Successfully processed item: {}", item.getTitle());

        } catch (Exception e) {
            log.error("Error during metadata extraction for file: {}", filePath, e);
            item.setStatus("ERROR");
            itemRepository.save(item);
        }
    }

    public String determineMediaType(String libraryType, String extension) {
        if ("MOVIE".equals(libraryType) || "TV_SHOW".equals(libraryType)) {
            return VIDEO_EXTENSIONS.contains(extension) ? libraryType : null;
        } else if ("AUDIO".equals(libraryType)) {
            return AUDIO_EXTENSIONS.contains(extension) ? "AUDIO" : null;
        } else if ("IMAGE".equals(libraryType)) {
            return IMAGE_EXTENSIONS.contains(extension) ? "IMAGE" : null;
        } else if ("MIXED".equals(libraryType)) {
            if (VIDEO_EXTENSIONS.contains(extension)) return "MOVIE";
            if (AUDIO_EXTENSIONS.contains(extension)) return "AUDIO";
            if (IMAGE_EXTENSIONS.contains(extension)) return "IMAGE";
            return null;
        }
        return null;
    }

    public String getExpectedExtensions(String libraryType) {
        return switch (libraryType) {
            case "MOVIE", "TV_SHOW" -> String.join(", ", VIDEO_EXTENSIONS);
            case "AUDIO" -> String.join(", ", AUDIO_EXTENSIONS);
            case "IMAGE" -> String.join(", ", IMAGE_EXTENSIONS);
            case "MIXED" -> String.join(", ", VIDEO_EXTENSIONS) + ", " +
                    String.join(", ", AUDIO_EXTENSIONS) + ", " +
                    String.join(", ", IMAGE_EXTENSIONS);
            default -> "unknown";
        };
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata value to JSON", e);
            return null;
        }
    }
}
