package com.mediamanager.streaming.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
    private final StoragePathMapper storagePathMapper;

    private static final long CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    @org.springframework.beans.factory.annotation.Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    public Resource getMediaResource(Integer fileId) {
        MediaFile mediaFile = loadReadableFile(fileId);
        Path filePath = resolveReadablePath(mediaFile);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                log.warn("Media file is not readable: id={}, storedPath={}, resolvedPath={}",
                        mediaFile.getId(), mediaFile.getFilePath(), filePath);
                throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
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
        Path filePath = resolveReadablePath(mediaFile);

        Path responsePath = resolveImageVariant(fileId, filePath, width);
        Resource resource = new FileSystemResource(responsePath);
        String contentType = Files.probeContentType(responsePath);
        if (contentType == null) {
            contentType = responsePath.toString().endsWith(".jpg") ? "image/jpeg" : "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Cache-Control", "public, max-age=86400")
                .body(resource);
    }

    public String mapPathForProcessing(String originalPath) {
        return storagePathMapper.mapPathIfNeeded(originalPath);
    }

    public Path resolveReadablePath(MediaFile mediaFile) {
        Path filePath = Paths.get(storagePathMapper.mapPathIfNeeded(mediaFile.getFilePath()));
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            log.warn("Media file not found: id={}, storedPath={}, resolvedPath={}, pathMappings={}, pathMapFrom={}, pathMapTo={}",
                    mediaFile.getId(),
                    mediaFile.getFilePath(),
                    filePath,
                    storagePathMapper.maskPathMappings(),
                    storagePathMapper.maskPathMapFrom(),
                    storagePathMapper.maskPathMapTo());
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        return filePath;
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

    private Path resolveImageVariant(Integer fileId, Path source, Integer width) throws IOException {
        if (width == null || width <= 0) {
            return source;
        }
        int targetWidth = Math.min(Math.max(width, 64), 2400);
        Path outDir = Path.of(cacheDir, "images", String.valueOf(fileId));
        Path out = outDir.resolve("w" + targetWidth + ".jpg");
        if (Files.exists(out)
                && Files.getLastModifiedTime(out).toMillis() >= Files.getLastModifiedTime(source).toMillis()) {
            return out;
        }

        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null || original.getWidth() <= targetWidth) {
            return source;
        }

        int targetHeight = Math.max(1, (int) Math.round(original.getHeight() * (targetWidth / (double) original.getWidth())));
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        Files.createDirectories(outDir);
        ImageIO.write(scaled, "jpg", out.toFile());
        return out;
    }
}
