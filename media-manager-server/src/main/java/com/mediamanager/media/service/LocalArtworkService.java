package com.mediamanager.media.service;

import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalArtworkService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "3gp", "asf", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4",
            "mpg", "mpeg", "mts", "ogv", "ts", "vob", "webm", "wmv"
    );
    private static final Set<String> GENERIC_POSTER_NAMES = Set.of("poster", "folder", "cover", "movie");
    private static final Set<String> GENERIC_BACKDROP_NAMES = Set.of("fanart", "backdrop", "background", "landscape");
    private static final Set<String> POSTER_SUFFIXES = Set.of("poster", "cover", "folder");
    private static final Set<String> BACKDROP_SUFFIXES = Set.of("fanart", "backdrop", "background", "landscape");

    private final MediaFileRepository fileRepository;
    private final MediaItemRepository itemRepository;

    @Transactional
    public void applyLocalArtwork(MediaItem item, MediaFile mediaFile, Path mediaPath) {
        if (item == null || item.getId() == null || mediaFile == null || mediaPath == null) {
            return;
        }
        Path parent = mediaPath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return;
        }
        String mediaBaseName = getBaseName(mediaPath.getFileName().toString());
        Optional<Path> poster = findArtwork(parent, mediaBaseName, ArtworkType.POSTER);
        Optional<Path> backdrop = findArtwork(parent, mediaBaseName, ArtworkType.BACKDROP);

        boolean changed = false;
        if (poster.isPresent()) {
            String posterPath = normalizePath(poster.get().toAbsolutePath().toString());
            if (!posterPath.equals(item.getPosterPath())) {
                item.setPosterPath(posterPath);
                changed = true;
            }
        }
        if (backdrop.isPresent()) {
            String backdropPath = normalizePath(backdrop.get().toAbsolutePath().toString());
            if (!backdropPath.equals(item.getBackdropPath())) {
                item.setBackdropPath(backdropPath);
                changed = true;
            }
        }
        if (changed) {
            itemRepository.save(item);
        }
    }

    @Transactional
    public boolean tryAttachArtworkFile(Path imagePath) {
        if (imagePath == null || !isArtworkImage(imagePath)) {
            return false;
        }
        Path parent = imagePath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return false;
        }
        String artworkBase = getBaseName(imagePath.getFileName().toString());
        ArtworkType genericType = classifyGenericArtwork(artworkBase);
        boolean attached = false;
        try (Stream<Path> files = Files.list(parent)) {
            for (Path videoPath : files
                    .filter(Files::isRegularFile)
                    .filter(LocalArtworkService::isVideoFile)
                    .toList()) {
                String mediaBase = getBaseName(videoPath.getFileName().toString());
                ArtworkType type = genericType != null ? genericType : classifyArtworkForMedia(artworkBase, mediaBase);
                if (type == null) {
                    continue;
                }
                Optional<MediaFile> mediaFile = findActiveMediaFile(videoPath);
                if (mediaFile.isEmpty()) {
                    continue;
                }
                MediaItem item = mediaFile.get().getMediaItem();
                String filePath = normalizePath(imagePath.toAbsolutePath().toString());
                if (type == ArtworkType.POSTER && !filePath.equals(item.getPosterPath())) {
                    item.setPosterPath(filePath);
                    itemRepository.save(item);
                    attached = true;
                } else if (type == ArtworkType.BACKDROP && !filePath.equals(item.getBackdropPath())) {
                    item.setBackdropPath(filePath);
                    itemRepository.save(item);
                    attached = true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to attach artwork {}: {}", imagePath, e.getMessage());
        }
        return attached;
    }

    private Optional<Path> findArtwork(Path parent, String mediaBaseName, ArtworkType type) {
        try (Stream<Path> files = Files.list(parent)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(LocalArtworkService::isArtworkImage)
                    .filter(path -> {
                        String artworkBase = getBaseName(path.getFileName().toString());
                        ArtworkType generic = classifyGenericArtwork(artworkBase);
                        if (generic == type) {
                            return true;
                        }
                        return classifyArtworkForMedia(artworkBase, mediaBaseName) == type;
                    })
                    .sorted(Comparator.comparingInt(path -> rankArtwork(path, mediaBaseName, type)))
                    .findFirst();
        } catch (IOException e) {
            log.debug("Failed to scan artwork directory {}", parent, e);
            return Optional.empty();
        }
    }

    private static int rankArtwork(Path path, String mediaBaseName, ArtworkType type) {
        String artworkBase = getBaseName(path.getFileName().toString()).toLowerCase(Locale.ROOT);
        String mediaBase = mediaBaseName.toLowerCase(Locale.ROOT);
        if (artworkBase.equals(mediaBase + "-poster") || artworkBase.equals(mediaBase + ".poster")) {
            return 0;
        }
        if (artworkBase.equals(mediaBase + "-fanart") || artworkBase.equals(mediaBase + ".fanart")) {
            return 0;
        }
        if (type == ArtworkType.POSTER && "poster".equals(artworkBase)) {
            return 1;
        }
        if (type == ArtworkType.BACKDROP && "fanart".equals(artworkBase)) {
            return 1;
        }
        return 10;
    }

    private Optional<MediaFile> findActiveMediaFile(Path videoPath) {
        String filePath = normalizePath(videoPath.toAbsolutePath().toString());
        return fileRepository.findByFilePath(filePath)
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .filter(file -> file.getMediaItem() != null);
    }

    private static ArtworkType classifyGenericArtwork(String artworkBase) {
        String lower = artworkBase.toLowerCase(Locale.ROOT);
        if (GENERIC_POSTER_NAMES.contains(lower)) {
            return ArtworkType.POSTER;
        }
        if (GENERIC_BACKDROP_NAMES.contains(lower)) {
            return ArtworkType.BACKDROP;
        }
        return null;
    }

    private static ArtworkType classifyArtworkForMedia(String artworkBase, String mediaBaseName) {
        String lower = artworkBase.toLowerCase(Locale.ROOT);
        String mediaLower = mediaBaseName.toLowerCase(Locale.ROOT);
        for (String suffix : POSTER_SUFFIXES) {
            if (lower.equals(mediaLower + "-" + suffix)
                    || lower.equals(mediaLower + "." + suffix)
                    || lower.equals(mediaLower + "_" + suffix)) {
                return ArtworkType.POSTER;
            }
        }
        for (String suffix : BACKDROP_SUFFIXES) {
            if (lower.equals(mediaLower + "-" + suffix)
                    || lower.equals(mediaLower + "." + suffix)
                    || lower.equals(mediaLower + "_" + suffix)) {
                return ArtworkType.BACKDROP;
            }
        }
        return null;
    }

    private static boolean isArtworkImage(Path path) {
        return path != null && IMAGE_EXTENSIONS.contains(getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT));
    }

    private static boolean isVideoFile(Path path) {
        return path != null && VIDEO_EXTENSIONS.contains(getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT));
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "" : fileName.substring(dot + 1);
    }

    private static String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    private enum ArtworkType {
        POSTER,
        BACKDROP
    }
}
