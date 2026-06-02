package com.mediamanager.streaming.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsStreamingService {

    private static final Set<String> NATIVE_CONTAINERS = Set.of("mp4", "webm", "m4v");

    private final MediaFileRepository fileRepository;
    private final StreamService streamService;
    private final LibraryAccessService libraryAccessService;
    private final SysConfigService sysConfigService;
    private final Map<Integer, Object> generationLocks = new ConcurrentHashMap<>();
    private final Map<Integer, Process> activeProcesses = new ConcurrentHashMap<>();

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    @Value("${mediamanager.hls.segment-duration:6}")
    private int segmentDuration;

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        log.info("Shutting down HlsStreamingService, terminating {} active FFmpeg processes...", activeProcesses.size());
        activeProcesses.forEach((fileId, process) -> {
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                log.debug("Error destroying process for file {}", fileId, e);
            }
        });
        activeProcesses.clear();
    }

    public void stopActiveProcess(Integer fileId) {
        Process p = activeProcesses.remove(fileId);
        if (p != null && p.isAlive()) {
            log.info("Terminating active FFmpeg process for fileId={}", fileId);
            p.destroyForcibly();
        }
    }

    public record PlaybackInfo(String mode, String url) {}

    public record TranscodeSpeedInfo(double speed, double fps, String time, String status) {}

    public TranscodeSpeedInfo getTranscodeSpeed(Integer fileId) {
        Process activeProcess = activeProcesses.get(fileId);
        if (activeProcess == null || !activeProcess.isAlive()) {
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "idle");
        }

        Path logPath = Path.of(cacheDir, "hls", String.valueOf(fileId), "ffmpeg-transcode.log");
        if (!Files.exists(logPath)) {
            logPath = Path.of(cacheDir, "hls", String.valueOf(fileId), "ffmpeg-copy.log");
        }

        if (!Files.exists(logPath)) {
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "starting");
        }

        try {
            List<String> lines;
            long fileSize = Files.size(logPath);
            if (fileSize > 10 * 1024 * 1024) {
                // File is larger than 10MB — tail-read last 100 lines to prevent OOM
                lines = tailReadLines(logPath, 100);
            } else {
                lines = Files.readAllLines(logPath);
            }
            if (lines.isEmpty()) {
                return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "starting");
            }
            double speed = 1.0;
            double fps = 0.0;
            String time = "00:00:00";
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);
                if (line.contains("speed=") || line.contains("fps=")) {
                    int fpsIdx = line.indexOf("fps=");
                    if (fpsIdx != -1) {
                        int spaceIdx = line.indexOf(' ', fpsIdx + 4);
                        if (spaceIdx == -1) spaceIdx = line.indexOf('\t', fpsIdx + 4);
                        String fpsStr = spaceIdx == -1 ? line.substring(fpsIdx + 4) : line.substring(fpsIdx + 4, spaceIdx);
                        try {
                            fps = Double.parseDouble(fpsStr.trim());
                        } catch (NumberFormatException ignored) {}
                    }

                    int speedIdx = line.indexOf("speed=");
                    if (speedIdx != -1) {
                        int spaceIdx = line.indexOf('x', speedIdx + 6);
                        String speedStr = spaceIdx == -1 ? line.substring(speedIdx + 6) : line.substring(speedIdx + 6, spaceIdx);
                        try {
                            speed = Double.parseDouble(speedStr.trim());
                        } catch (NumberFormatException ignored) {}
                    }

                    int timeIdx = line.indexOf("time=");
                    if (timeIdx != -1) {
                        int spaceIdx = line.indexOf(' ', timeIdx + 5);
                        if (spaceIdx == -1) spaceIdx = line.indexOf('\t', timeIdx + 5);
                        time = spaceIdx == -1 ? line.substring(timeIdx + 5) : line.substring(timeIdx + 5, spaceIdx);
                        time = time.trim();
                    }
                    break;
                }
            }
            // Validate parsed values against edge cases (N/A, negative, NaN)
            if (Double.isNaN(speed) || Double.isInfinite(speed) || speed < 0) speed = 0.0;
            if (Double.isNaN(fps) || Double.isInfinite(fps) || fps < 0) fps = 0.0;
            if (time == null || time.isBlank() || !time.contains(":")) time = "00:00:00";
            return new TranscodeSpeedInfo(speed, fps, time, "active");
        } catch (NoSuchFileException e) {
            // Log file disappeared between exists-check and read (race condition)
            log.debug("Transcode log file disappeared for file {}", fileId);
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "starting");
        } catch (IOException e) {
            log.debug("Error reading transcode log for file {}", fileId, e);
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "error");
        }
    }

    public PlaybackInfo resolvePlaybackInfo(Integer fileId) {
        MediaFile file = loadAuthorizedFile(fileId);
        String container = file.getContainer() != null
                ? file.getContainer().toLowerCase(Locale.ROOT)
                : guessContainer(file.getFilePath());
        if (container != null && NATIVE_CONTAINERS.contains(container)) {
            streamService.resolveReadablePath(file);
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
            Process activeProcess = activeProcesses.get(fileId);
            if (activeProcess != null && activeProcess.isAlive()) {
                log.debug("Segment {} not ready yet, waiting for active FFmpeg process...", segmentName);
                for (int i = 0; i < 50; i++) { // wait up to 5s
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (Files.exists(segment)) {
                        break;
                    }
                    if (!activeProcess.isAlive()) {
                        break;
                    }
                }
            }

            if (!Files.exists(segment) && activeProcess != null && !activeProcess.isAlive() && activeProcess.exitValue() != 0) {
                log.warn("Active HLS process for file {} crashed with exit code {}. Re-triggering HLS in full transcode mode...", fileId, activeProcess.exitValue());
                try {
                    activeProcesses.remove(fileId, activeProcess);
                    Path outDir = Path.of(cacheDir, "hls", String.valueOf(fileId));
                    Path playlist = outDir.resolve("master.m3u8");
                    Path source = streamService.resolveReadablePath(file);
                    cleanupOldHlsFiles(outDir);
                    runFfmpegHlsAsync(source, outDir, playlist, false);
                    for (int i = 0; i < 50; i++) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (Files.exists(segment)) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to run self-healing HLS transcode for file {}", fileId, e);
                }
            }
        }

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
        Path source = streamService.resolveReadablePath(file);
        
        if (Files.exists(playlist) && Files.exists(source)) {
            try {
                long srcMtime = Files.getLastModifiedTime(source).toMillis();
                long plMtime = Files.getLastModifiedTime(playlist).toMillis();
                if (plMtime >= srcMtime) {
                    return playlist;
                }
            } catch (IOException e) {
                log.debug("Error checking last modified time for file {}", file.getId(), e);
            }
        }

        Object lock = generationLocks.computeIfAbsent(file.getId(), id -> new Object());
        synchronized (lock) {
            try {
                Process activeProcess = activeProcesses.get(file.getId());
                if (activeProcess != null && activeProcess.isAlive()) {
                    waitForPlaylist(playlist);
                    return playlist;
                }

                if (Files.exists(playlist) && Files.exists(source)) {
                    long srcMtime = Files.getLastModifiedTime(source).toMillis();
                    long plMtime = Files.getLastModifiedTime(playlist).toMillis();
                    if (plMtime >= srcMtime) {
                        return playlist;
                    }
                }

                Files.createDirectories(outDir);
                cleanupOldHlsFiles(outDir);

                log.info("Starting asynchronous HLS generation for fileId={} (stream-copy)", file.getId());
                runFfmpegHlsAsync(source, outDir, playlist, true);
                
                boolean playlistCreated = waitForPlaylist(playlist);
                if (!playlistCreated) {
                    Process p = activeProcesses.get(file.getId());
                    if (p != null && !p.isAlive() && p.exitValue() != 0) {
                        log.info("HLS stream-copy process exited early with code {}, retrying with browser-safe transcode...", p.exitValue());
                        cleanupOldHlsFiles(outDir);
                        runFfmpegHlsAsync(source, outDir, playlist, false);
                        playlistCreated = waitForPlaylist(playlist);
                    }
                }

                if (!playlistCreated) {
                    throw new BusinessException(ErrorCode.FFMPEG_ERROR, "Asynchronous HLS playlist generation timed out");
                }
                return playlist;
            } catch (IOException e) {
                log.error("HLS generation failed for file {}", file.getId(), e);
                throw new BusinessException(ErrorCode.FFMPEG_ERROR, "HLS generation failed");
            } finally {
                generationLocks.remove(file.getId(), lock);
            }
        }
    }

    private void runFfmpegHlsAsync(Path source, Path outDir, Path playlist, boolean streamCopy) throws IOException {
        if (!Files.exists(source)) {
            throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Source file not found: " + source);
        }
        
        Integer fileId = Integer.parseInt(outDir.getFileName().toString());
        stopActiveProcess(fileId);

        String segmentPattern = outDir.resolve("%04d.ts").toString();
        ProcessBuilder pb;
        if (streamCopy) {
            pb = new ProcessBuilder(
                    sysConfigService.ffmpegPath(yamlFfmpegPath),
                    "-y",
                    "-i", source.toString(),
                    "-c", "copy",
                    "-f", "hls",
                    "-hls_time", String.valueOf(segmentDuration),
                    "-hls_list_size", "0",
                    "-hls_segment_filename", segmentPattern,
                    playlist.toString()
            );
        } else {
            pb = new ProcessBuilder(
                    sysConfigService.ffmpegPath(yamlFfmpegPath),
                    "-y",
                    "-i", source.toString(),
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "160k",
                    "-f", "hls",
                    "-hls_time", String.valueOf(segmentDuration),
                    "-hls_list_size", "0",
                    "-hls_segment_filename", segmentPattern,
                    playlist.toString()
            );
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(outDir.resolve(streamCopy ? "ffmpeg-copy.log" : "ffmpeg-transcode.log").toFile());
        
        log.debug("Launching FFmpeg process asynchronously for fileId={}. Command: {}", fileId, String.join(" ", pb.command()));
        Process process = pb.start();
        activeProcesses.put(fileId, process);

        Thread.ofVirtual().name("ffmpeg-watcher-" + fileId).start(() -> {
            try {
                int exitCode = process.waitFor();
                log.info("FFmpeg process for fileId={} completed with exit code {}", fileId, exitCode);
            } catch (InterruptedException e) {
                log.warn("FFmpeg watcher thread interrupted for fileId={}", fileId);
                Thread.currentThread().interrupt();
            } finally {
                activeProcesses.remove(fileId, process);
            }
        });
    }

    private boolean waitForPlaylist(Path playlist) {
        for (int i = 0; i < 30; i++) { // 3 seconds
            if (Files.exists(playlist)) {
                try {
                    if (Files.size(playlist) > 0) {
                        return true;
                    }
                } catch (IOException ignored) {}
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void cleanupOldHlsFiles(Path outDir) throws IOException {
        if (!Files.exists(outDir)) {
            return;
        }
        try (var stream = Files.list(outDir)) {
            stream.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".ts") || name.endsWith(".m3u8");
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Failed to delete old HLS file {}", path, e);
                        }
                    });
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

    /**
     * Reads approximately the last {@code maxLines} lines from a file without loading the entire file into memory.
     * Used to safely read very large FFmpeg log files.
     */
    private List<String> tailReadLines(Path filePath, int maxLines) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return List.of();
            }

            // Read up to 64KB from the end — more than enough for 100 short FFmpeg status lines
            int readSize = (int) Math.min(fileLength, 64 * 1024);
            byte[] buffer = new byte[readSize];
            raf.seek(fileLength - readSize);
            raf.readFully(buffer);

            String tail = new String(buffer);
            String[] allLines = tail.split("\n");

            int start = Math.max(0, allLines.length - maxLines);
            // Skip the first partial line if we didn't read from the beginning of the file
            if (fileLength > readSize && start == 0) {
                start = 1;
            }
            return List.of(java.util.Arrays.copyOfRange(allLines, start, allLines.length));
        }
    }

    public Set<Integer> getActiveStreamingFileIds() {
        return java.util.Collections.unmodifiableSet(activeProcesses.keySet());
    }
}
