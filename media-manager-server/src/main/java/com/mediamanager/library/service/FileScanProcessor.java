package com.mediamanager.library.service;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.MediaPostProcessService;
import com.mediamanager.metadata.service.MetadataApplyService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class FileScanProcessor {

    private final MediaItemRepository itemRepository;
    private final MediaFileRepository fileRepository;
    private final MetadataPipelineService pipelineService;
    private final MetadataApplyService metadataApplyService;
    private final FileNameParser fileNameParser;
    private final MediaPostProcessService mediaPostProcessService;

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

    public String checkFile(MediaLibrary library, Path file) {
        String fileName = file.getFileName().toString();
        String extension = getExtension(fileName).toLowerCase();
        String mediaType = determineMediaType(library.getType(), extension);
        if (mediaType == null) {
            return null;
        }
        String filePath = file.toAbsolutePath().toString();
        if (fileRepository.existsByFilePathAndNotDeleted(filePath)) {
            return null;
        }
        return mediaType;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFile(MediaLibrary library, Path file, BasicFileAttributes attrs, String mediaType) {
        String fileName = file.getFileName().toString();
        String filePath = file.toAbsolutePath().toString();

        log.debug("Found new file: {}", filePath);

        MediaItem item = MediaItem.builder()
                .library(library)
                .title(fileName)
                .originalTitle(fileName)
                .type(mediaType)
                .status("UNIDENTIFIED")
                .lastScannedAt(Instant.now())
                .build();
        itemRepository.save(item);

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

        try {
            MetadataResult pipelineResult = pipelineService.executeLocalPipeline(item, mediaFile);
            if (pipelineResult.getTitle() == null) {
                MetadataResult fallback = fileNameParser.parse(fileName);
                pipelineResult.mergeFrom(fallback);
            }
            metadataApplyService.applyResult(item, pipelineResult, mediaFile);
            mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
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
            if (VIDEO_EXTENSIONS.contains(extension)) {
                return "MOVIE";
            }
            if (AUDIO_EXTENSIONS.contains(extension)) {
                return "AUDIO";
            }
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return "IMAGE";
            }
            return null;
        }
        return null;
    }

    public String getExpectedExtensions(String libraryType) {
        return switch (libraryType) {
            case "MOVIE", "TV_SHOW" -> String.join(", ", VIDEO_EXTENSIONS);
            case "AUDIO" -> String.join(", ", AUDIO_EXTENSIONS);
            case "IMAGE" -> String.join(", ", IMAGE_EXTENSIONS);
            case "MIXED" -> String.join(", ", VIDEO_EXTENSIONS) + ", "
                    + String.join(", ", AUDIO_EXTENSIONS) + ", "
                    + String.join(", ", IMAGE_EXTENSIONS);
            default -> "unknown";
        };
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }
}
