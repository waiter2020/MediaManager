package com.mediamanager.streaming.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private final MediaFileRepository fileRepository;
    private final LibraryAccessService libraryAccessService;

    private static final long CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    /**
     * Optional path mapping to make persisted host paths work inside containers.
     * Example (Windows Docker Desktop):
     *   from: E:\Movies
     *   to:   /home/media
     */
    @Value("${mediamanager.storage.path-map-from:}")
    private String pathMapFrom;

    @Value("${mediamanager.storage.path-map-to:}")
    private String pathMapTo;

    public Resource getMediaResource(Integer fileId) {
        MediaFile mediaFile = loadReadableFile(fileId);
        Path filePath = Paths.get(mapPathIfNeeded(mediaFile.getFilePath()));
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Could not read file: " + filePath);
            }
        } catch (MalformedURLException e) {
            log.error("Malformed URL for file path: {}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Malformed URL for file path: " + filePath);
        }
    }

    public ResourceRegion getResourceRegion(Resource resource, HttpHeaders headers) throws IOException {
        long contentLength = resource.contentLength();
        List<HttpRange> ranges = headers.getRange();

        if (!ranges.isEmpty()) {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(CHUNK_SIZE, end - start + 1);
            return new ResourceRegion(resource, start, rangeLength);
        } else {
            long rangeLength = Math.min(CHUNK_SIZE, contentLength);
            return new ResourceRegion(resource, 0, rangeLength);
        }
    }

    public ResponseEntity<Resource> getImageResource(Integer fileId, Integer width) throws IOException {
        MediaFile mediaFile = loadReadableFile(fileId);
        Path filePath = Path.of(mapPathIfNeeded(mediaFile.getFilePath()));
        if (!Files.exists(filePath)) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }

        Resource resource = new FileSystemResource(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "image/jpeg";

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(resource);
    }

    public String mapPathForProcessing(String originalPath) {
        return mapPathIfNeeded(originalPath);
    }

    private MediaFile loadReadableFile(Integer fileId) {
        MediaFile mediaFile = fileRepository.findByIdWithItemAndLibrary(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        if (Boolean.TRUE.equals(mediaFile.getDeleted())) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        if (mediaFile.getMediaItem() != null && Boolean.TRUE.equals(mediaFile.getMediaItem().getHidden())) {
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        libraryAccessService.assertCanViewFile(mediaFile);
        return mediaFile;
    }

    private String mapPathIfNeeded(String originalPath) {
        if (originalPath == null || originalPath.isBlank()) {
            return originalPath;
        }
        if (pathMapFrom == null || pathMapFrom.isBlank() || pathMapTo == null || pathMapTo.isBlank()) {
            return originalPath;
        }

        // Normalize to forward slashes for prefix comparison
        String p = originalPath.replace('\\', '/');
        String from = pathMapFrom.replace('\\', '/');
        String to = pathMapTo.replace('\\', '/');

        if (p.regionMatches(true, 0, from, 0, from.length())) {
            String suffix = p.substring(from.length());
            if (!suffix.startsWith("/")) suffix = "/" + suffix;
            String mapped = to.endsWith("/") ? to.substring(0, to.length() - 1) : to;
            return mapped + suffix;
        }

        return originalPath;
    }
}
