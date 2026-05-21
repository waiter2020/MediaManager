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
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsStreamingService {

    private static final Set<String> NATIVE_CONTAINERS = Set.of("mp4", "webm", "m4v");

    private final MediaFileRepository fileRepository;
    private final StreamService streamService;
    private final LibraryAccessService libraryAccessService;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    @Value("${mediamanager.hls.segment-duration:6}")
    private int segmentDuration;

    public record PlaybackInfo(String mode, String url) {}

    public PlaybackInfo resolvePlaybackInfo(Integer fileId) {
        MediaFile file = loadAuthorizedFile(fileId);
        String container = file.getContainer() != null
                ? file.getContainer().toLowerCase(Locale.ROOT)
                : guessContainer(file.getFilePath());
        if (container != null && NATIVE_CONTAINERS.contains(container)) {
            return new PlaybackInfo("direct", "/api/v1/stream/raw/" + fileId);
        }
        ensureHlsPlaylist(file);
        return new PlaybackInfo("hls", "/api/v1/stream/" + fileId + "/hls/master.m3u8");
    }

    public Resource getMasterPlaylist(Integer fileId) {
        MediaFile file = loadAuthorizedFile(fileId);
        Path playlist = ensureHlsPlaylist(file);
        return new FileSystemResource(playlist);
    }

    public Resource getSegment(Integer fileId, String segmentName) {
        if (segmentName.contains("..") || segmentName.contains("/") || segmentName.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Invalid segment name");
        }
        MediaFile file = loadAuthorizedFile(fileId);
        Path segment = Path.of(cacheDir, "hls", String.valueOf(fileId), segmentName);
        if (!Files.exists(segment)) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        return new FileSystemResource(segment);
    }

    private MediaFile loadAuthorizedFile(Integer fileId) {
        MediaFile file = fileRepository.findByIdWithItemAndLibrary(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        if (Boolean.TRUE.equals(file.getDeleted())) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND);
        }
        if (file.getMediaItem() != null && Boolean.TRUE.equals(file.getMediaItem().getHidden())) {
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        libraryAccessService.assertCanViewFile(file);
        return file;
    }

    private Path ensureHlsPlaylist(MediaFile file) {
        Path outDir = Path.of(cacheDir, "hls", String.valueOf(file.getId()));
        Path playlist = outDir.resolve("master.m3u8");
        Path source = Path.of(streamService.mapPathForProcessing(file.getFilePath()));

        try {
            if (Files.exists(playlist) && Files.exists(source)) {
                long srcMtime = Files.getLastModifiedTime(source).toMillis();
                long plMtime = Files.getLastModifiedTime(playlist).toMillis();
                if (plMtime >= srcMtime) {
                    return playlist;
                }
            }
            Files.createDirectories(outDir);
            runFfmpegHls(source, outDir, playlist);
            return playlist;
        } catch (IOException e) {
            log.error("HLS generation failed for file {}", file.getId(), e);
            throw new BusinessException(ErrorCode.FFMPEG_ERROR, "HLS generation failed");
        }
    }

    private void runFfmpegHls(Path source, Path outDir, Path playlist) throws IOException {
        if (!Files.exists(source)) {
            throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Source file not found: " + source);
        }
        String segmentPattern = outDir.resolve("%04d.ts").toString();
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",
                "-i", source.toString(),
                "-c", "copy",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentDuration),
                "-hls_list_size", "0",
                "-hls_segment_filename", segmentPattern,
                playlist.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try {
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.FFMPEG_ERROR, "FFmpeg HLS timeout");
            }
            if (process.exitValue() != 0) {
                try (BufferedReader reader = process.inputReader()) {
                    String output = reader.lines().reduce("", (a, b) -> a + "\n" + b);
                    log.warn("FFmpeg output: {}", output);
                }
                throw new BusinessException(ErrorCode.FFMPEG_ERROR, "FFmpeg exited with code " + process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.FFMPEG_ERROR, "FFmpeg interrupted");
        }
    }

    private String guessContainer(String path) {
        if (path == null) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
