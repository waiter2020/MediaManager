package com.mediamanager.library.service;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.LocalArtworkService;
import com.mediamanager.media.service.MediaPostProcessService;
import com.mediamanager.media.service.MediaSubtitleService;
import com.mediamanager.metadata.service.MetadataApplyService;
import com.mediamanager.metadata.service.MetadataPipelineService;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.util.FileNameParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
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
    private final MediaSubtitleService mediaSubtitleService;
    private final LocalArtworkService localArtworkService;

    public enum ScanOutcome {
        SKIPPED(false),
        UNCHANGED(true),
        CREATED(true),
        UPDATED(true),
        RESTORED(true),
        FAILED(true);

        private final boolean matchedMediaFile;

        ScanOutcome(boolean matchedMediaFile) {
            this.matchedMediaFile = matchedMediaFile;
        }

        public boolean matchedMediaFile() {
            return matchedMediaFile;
        }
    }

    public record ScanResult(ScanOutcome outcome, String errorMessage) {
        public static ScanResult of(ScanOutcome outcome) {
            return new ScanResult(outcome, null);
        }

        public static ScanResult failed(Throwable error) {
            return new ScanResult(ScanOutcome.FAILED, describeError(error));
        }

        public static ScanResult failed(String errorMessage) {
            return new ScanResult(ScanOutcome.FAILED, errorMessage);
        }

        public boolean matchedMediaFile() {
            return outcome.matchedMediaFile();
        }
    }

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

    @Transactional
    public ScanResult scanFile(MediaLibrary library, Path file, BasicFileAttributes attrs) {
        String fileName = file.getFileName().toString();
        String extension = getExtension(fileName).toLowerCase();
        String mediaType = determineMediaType(library.getType(), extension);
        if (mediaType == null) {
            if (mediaSubtitleService.tryAttachSubtitleFile(file, attrs)) {
                return ScanResult.of(ScanOutcome.UPDATED);
            }
            if (localArtworkService.tryAttachArtworkFile(file)) {
                return ScanResult.of(ScanOutcome.UPDATED);
            }
            return ScanResult.of(ScanOutcome.SKIPPED);
        }

        String filePath = normalizePath(file.toAbsolutePath().toString());
        MediaFile existing = fileRepository.findByFilePath(filePath).orElse(null);
        if (existing != null && !Boolean.TRUE.equals(existing.getDeleted()) && !hasFileChanged(existing, attrs)) {
            if (existing.getMediaItem() != null) {
                existing.getMediaItem().setLastScannedAt(Instant.now());
                itemRepository.save(existing.getMediaItem());
            }
            return ScanResult.of(ScanOutcome.UNCHANGED);
        }

        try {
            processFile(library, file, attrs, mediaType);
            if (existing == null) {
                return ScanResult.of(ScanOutcome.CREATED);
            }
            ScanOutcome outcome = Boolean.TRUE.equals(existing.getDeleted())
                    ? ScanOutcome.RESTORED
                    : ScanOutcome.UPDATED;
            return ScanResult.of(outcome);
        } catch (Exception e) {
            log.error("Failed to scan media file: {}", filePath, e);
            return ScanResult.failed(e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFile(MediaLibrary library, Path file, BasicFileAttributes attrs, String mediaType) {
        String fileName = file.getFileName().toString();
        String filePath = normalizePath(file.toAbsolutePath().toString());

        log.debug("Found new file: {}", filePath);

        MetadataResult fileNameResult = fileNameParser.parse(fileName);
        MediaFile mediaFile = fileRepository.findByFilePath(filePath).orElseGet(MediaFile::new);
        MediaItem item = mediaFile.getMediaItem() != null
                ? mediaFile.getMediaItem()
                : resolveItemForFile(library, fileName, mediaType, fileNameResult);

        String extension = getExtension(fileName).toLowerCase();
        mediaFile.setMediaItem(item);
        mediaFile.setFilePath(filePath);
        mediaFile.setFileName(fileName);
        mediaFile.setFileSize(attrs.size());
        mediaFile.setContainer(extension);
        mediaFile.setFileModifiedAt(attrs.lastModifiedTime().toInstant());
        mediaFile.setDeleted(false);
        mediaFile.setDeletedAt(null);
        item.setHidden(false);
        item.setLastScannedAt(Instant.now());
        itemRepository.save(item);
        fileRepository.save(mediaFile);

        try {
            MetadataResult pipelineResult = pipelineService.executeLocalPipeline(item, mediaFile);
            pipelineResult.mergeFrom(fileNameResult);
            metadataApplyService.applyResult(item, pipelineResult, mediaFile);
            localArtworkService.applyLocalArtwork(item, mediaFile, file);
            mediaSubtitleService.syncLocalSubtitles(item, mediaFile, file);
            enqueuePostProcessAfterCommit(item.getId());
            log.info("Successfully processed item: {}", item.getTitle());
        } catch (Exception e) {
            log.error("Error during metadata extraction for file: {}", filePath, e);
            item.setStatus("ERROR");
            itemRepository.save(item);
        }
    }

    private MediaItem resolveItemForFile(
            MediaLibrary library,
            String fileName,
            String mediaType,
            MetadataResult fileNameResult) {
        if ("TV_SHOW".equals(mediaType)
                && fileNameResult.getSeasonNumber() != null
                && fileNameResult.getEpisodeNumber() != null
                && fileNameResult.getOriginalTitle() != null
                && !fileNameResult.getOriginalTitle().isBlank()) {
            String show = fileNameResult.getOriginalTitle();
            String episodeTitle = String.format("%s S%02dE%02d",
                    show,
                    fileNameResult.getSeasonNumber(),
                    fileNameResult.getEpisodeNumber());
            return itemRepository
                    .findTitleCandidates(library.getId(), "EPISODE", episodeTitle)
                    .stream()
                    .findFirst()
                    .orElseGet(() -> createItem(library, episodeTitle, show, "EPISODE"));
        }

        String title = fileNameResult.getTitle() != null ? fileNameResult.getTitle() : fileName;
        String originalTitle = fileNameResult.getOriginalTitle() != null
                ? fileNameResult.getOriginalTitle()
                : fileName;
        return createItem(library, title, originalTitle, mediaType);
    }

    private MediaItem createItem(MediaLibrary library, String title, String originalTitle, String mediaType) {
        MediaItem item = MediaItem.builder()
                .library(library)
                .title(title)
                .originalTitle(originalTitle)
                .type(mediaType)
                .status("UNIDENTIFIED")
                .lastScannedAt(Instant.now())
                .build();
        return itemRepository.save(item);
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

    private boolean hasFileChanged(MediaFile file, BasicFileAttributes attrs) {
        Instant currentModifiedAt = attrs.lastModifiedTime().toInstant();
        if (!Objects.equals(file.getFileSize(), attrs.size())) {
            return true;
        }
        if (file.getFileModifiedAt() == null) {
            return true;
        }
        return file.getFileModifiedAt().toEpochMilli() != currentModifiedAt.toEpochMilli();
    }

    private String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    private void enqueuePostProcessAfterCommit(Integer itemId) {
        if (itemId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            mediaPostProcessService.afterMetadataUpdatedAsync(itemId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mediaPostProcessService.afterMetadataUpdatedAsync(itemId);
            }
        });
    }

    private static String describeError(Throwable error) {
        if (error == null) {
            return "Unknown scan error";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message;
    }
}
