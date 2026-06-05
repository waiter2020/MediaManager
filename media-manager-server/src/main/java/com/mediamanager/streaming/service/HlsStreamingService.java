package com.mediamanager.streaming.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.streaming.dto.PlaybackInfoResponse;
import com.mediamanager.streaming.dto.PlaybackProfile;
import com.mediamanager.streaming.dto.PlaybackQuality;
import com.mediamanager.streaming.dto.TranscodeMode;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SysConfigService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class HlsStreamingService {

    private static final Set<String> DIRECT_MP4_CONTAINERS = Set.of("mp4", "m4v", "mov");
    private static final Set<String> DIRECT_WEBM_CONTAINERS = Set.of("webm");
    private static final Set<String> DIRECT_AUDIO_CONTAINERS = Set.of("mp3", "aac", "m4a", "ogg", "oga", "opus", "wav");
    private static final Set<String> H264_CODECS = Set.of("h264", "avc", "avc1", "x264");
    private static final Set<String> WEBM_VIDEO_CODECS = Set.of("vp8", "vp9", "av1");
    private static final Set<String> HLS_AUDIO_COPY_CODECS = Set.of("aac", "mp3");
    private static final Set<String> DIRECT_AUDIO_CODECS = Set.of("aac", "mp3", "opus", "vorbis", "wav", "pcm");

    private final MediaFileRepository fileRepository;
    private final StreamService streamService;
    private final LibraryAccessService libraryAccessService;
    private final SysConfigService sysConfigService;
    private final Map<String, Object> generationLocks = new ConcurrentHashMap<>();
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.playback.hardware-encoder:h264_nvenc}")
    private String yamlHardwareEncoder;

    @Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    @Value("${mediamanager.hls.segment-duration:6}")
    private int segmentDuration;

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down HlsStreamingService, terminating {} active FFmpeg processes...", activeProcesses.size());
        activeProcesses.forEach((key, process) -> {
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                log.debug("Error destroying process for {}", key, e);
            }
        });
        activeProcesses.clear();
    }

    public void stopActiveProcess(Integer fileId) {
        List<String> keys = activeProcesses.keySet().stream()
                .filter(key -> key.startsWith(fileId + ":"))
                .toList();
        keys.forEach(this::stopActiveProcess);
    }

    public record TranscodeSpeedInfo(double speed, double fps, String time, String status) {}

    public TranscodeSpeedInfo getTranscodeSpeed(Integer fileId) {
        return getTranscodeSpeed(fileId, null);
    }

    public TranscodeSpeedInfo getTranscodeSpeed(Integer fileId, String variant) {
        Optional<String> key = resolveTelemetryKey(fileId, variant);
        if (key.isEmpty()) {
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "idle");
        }

        Process activeProcess = activeProcesses.get(key.get());
        if (activeProcess == null || !activeProcess.isAlive()) {
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "idle");
        }

        String activeVariant = variantFromProcessKey(key.get());
        Path logPath = latestLogPath(fileId, activeVariant);
        if (logPath == null || !Files.exists(logPath)) {
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "starting");
        }

        try {
            List<String> lines;
            long fileSize = Files.size(logPath);
            if (fileSize > 10 * 1024 * 1024) {
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
                    fps = parseFfmpegDouble(line, "fps=", fps);
                    speed = parseFfmpegDouble(line, "speed=", speed);
                    time = parseFfmpegToken(line, "time=").orElse(time);
                    break;
                }
            }

            if (Double.isNaN(speed) || Double.isInfinite(speed) || speed < 0) speed = 0.0;
            if (Double.isNaN(fps) || Double.isInfinite(fps) || fps < 0) fps = 0.0;
            if (time == null || time.isBlank() || !time.contains(":")) time = "00:00:00";
            return new TranscodeSpeedInfo(speed, fps, time, "active");
        } catch (NoSuchFileException e) {
            log.debug("Transcode log file disappeared for file {}", fileId);
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "starting");
        } catch (IOException e) {
            log.debug("Error reading transcode log for file {}", fileId, e);
            return new TranscodeSpeedInfo(0.0, 0.0, "00:00:00", "error");
        }
    }

    public PlaybackInfoResponse resolvePlaybackInfo(
            Integer fileId,
            String requestedMode,
            String requestedQuality,
            String requestedTranscodeMode) {
        MediaFile file = loadAuthorizedFile(fileId);
        PlaybackProfile profile = PlaybackProfile.of(requestedQuality, requestedTranscodeMode);
        String mode = normalizePlaybackMode(requestedMode);
        boolean directPlayable = isDirectPlayable(file);
        boolean forceDirect = "direct".equals(mode);
        boolean forceHls = "hls".equals(mode)
                || profile.effectiveQuality().constrained()
                || profile.transcodeMode() != TranscodeMode.AUTO;

        if (forceDirect || (!forceHls && directPlayable)) {
            streamService.resolveReadablePath(file);
            return toPlaybackInfo(file, profile, "direct", "DirectPlay",
                    "/api/v1/stream/raw/" + fileId, directPlayable, false, buildReasons(file, profile, mode, directPlayable));
        }

        TranscodePlan plan = buildTranscodePlan(file, profile);
        ensureHlsPlaylist(file, profile, plan);
        String playMethod = plan.strategy == EncodingStrategy.STREAM_COPY ? "DirectStream" : "Transcode";
        return toPlaybackInfo(file, profile, "hls", playMethod,
                "/api/v1/stream/" + fileId + "/hls/" + profile.variantKey() + "/master.m3u8",
                directPlayable, plan.strategy != EncodingStrategy.STREAM_COPY,
                buildReasons(file, profile, mode, directPlayable));
    }

    public Resource getMasterPlaylist(Integer fileId) {
        return getMasterPlaylist(fileId, new PlaybackProfile(PlaybackQuality.AUTO, TranscodeMode.AUTO));
    }

    public Resource getMasterPlaylist(Integer fileId, PlaybackProfile profile) {
        MediaFile file = loadAuthorizedFile(fileId);
        Path playlist = ensureHlsPlaylist(file, profile, buildTranscodePlan(file, profile));
        return new FileSystemResource(playlist);
    }

    public Resource getSegment(Integer fileId, String segmentName) {
        return getSegment(fileId, new PlaybackProfile(PlaybackQuality.AUTO, TranscodeMode.AUTO).variantKey(), segmentName);
    }

    public Resource getSegment(Integer fileId, String variant, String segmentName) {
        if (segmentName.contains("..") || segmentName.contains("/") || segmentName.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Invalid segment name");
        }
        PlaybackProfile profile = PlaybackProfile.fromVariant(variant);
        MediaFile file = loadAuthorizedFile(fileId);
        Path segment = profileDir(fileId, profile).resolve(segmentName);
        waitForSegmentIfActive(fileId, profile, segment);

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

    private Path ensureHlsPlaylist(MediaFile file, PlaybackProfile profile, TranscodePlan plan) {
        Path outDir = profileDir(file.getId(), profile);
        Path playlist = outDir.resolve("master.m3u8");
        Path source = streamService.resolveReadablePath(file);

        if (isFreshPlaylist(playlist, source)) {
            return playlist;
        }

        String key = processKey(file.getId(), profile);
        Object lock = generationLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                Process activeProcess = activeProcesses.get(key);
                if (activeProcess != null && activeProcess.isAlive()) {
                    waitForPlaylist(playlist);
                    return playlist;
                }

                if (isFreshPlaylist(playlist, source)) {
                    return playlist;
                }

                Files.createDirectories(outDir);
                cleanupOldHlsFiles(outDir);

                log.info("Starting HLS generation for fileId={}, variant={}, strategy={}",
                        file.getId(), profile.variantKey(), plan.strategy);
                runFfmpegHlsAsync(file, profile, source, outDir, playlist, plan);

                boolean playlistCreated = waitForPlaylist(playlist);
                Process process = activeProcesses.get(key);
                if (!playlistCreated
                        && plan.strategy == EncodingStrategy.STREAM_COPY
                        && process != null
                        && !process.isAlive()
                        && process.exitValue() != 0) {
                    log.info("HLS stream-copy for fileId={} variant={} exited with code {}, retrying software transcode",
                            file.getId(), profile.variantKey(), process.exitValue());
                    cleanupOldHlsFiles(outDir);
                    TranscodePlan fallback = new TranscodePlan(EncodingStrategy.SOFTWARE, null);
                    runFfmpegHlsAsync(file, profile, source, outDir, playlist, fallback);
                    playlistCreated = waitForPlaylist(playlist);
                }

                if (!playlistCreated) {
                    throw new BusinessException(ErrorCode.FFMPEG_ERROR, "HLS playlist generation timed out");
                }
                return playlist;
            } catch (IOException e) {
                log.error("HLS generation failed for file {}", file.getId(), e);
                throw new BusinessException(ErrorCode.FFMPEG_ERROR, "HLS generation failed");
            } finally {
                generationLocks.remove(key, lock);
            }
        }
    }

    private void runFfmpegHlsAsync(
            MediaFile file,
            PlaybackProfile profile,
            Path source,
            Path outDir,
            Path playlist,
            TranscodePlan plan) throws IOException {
        if (!Files.exists(source)) {
            throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Source file not found: " + source);
        }

        String key = processKey(file.getId(), profile);
        stopActiveProcess(key);

        String segmentPattern = outDir.resolve("%04d.ts").toString();
        List<String> command = new ArrayList<>();
        command.add(sysConfigService.ffmpegPath(yamlFfmpegPath));
        command.addAll(List.of(
                "-hide_banner",
                "-y",
                "-i", source.toString(),
                "-map", "0:v:0?",
                "-map", "0:a:0?",
                "-sn"
        ));

        if (plan.strategy == EncodingStrategy.STREAM_COPY) {
            command.addAll(List.of("-c", "copy"));
        } else {
            addVideoTranscodeArgs(command, file, profile, plan);
            command.addAll(List.of("-c:a", "aac", "-b:a", "160k", "-ac", "2"));
        }

        command.addAll(List.of(
                "-f", "hls",
                "-hls_time", String.valueOf(segmentDuration),
                "-hls_list_size", "0",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", segmentPattern,
                "-hls_base_url", "/api/v1/stream/" + file.getId() + "/hls/" + profile.variantKey() + "/",
                playlist.toString()
        ));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(outDir.resolve(plan.logFileName()).toFile());

        log.debug("Launching FFmpeg process for {}. Command: {}", key, String.join(" ", pb.command()));
        Process process = pb.start();
        activeProcesses.put(key, process);

        Thread.ofVirtual().name("ffmpeg-watcher-" + key.replace(':', '-')).start(() -> {
            try {
                int exitCode = process.waitFor();
                log.info("FFmpeg process for {} completed with exit code {}", key, exitCode);
            } catch (InterruptedException e) {
                log.warn("FFmpeg watcher thread interrupted for {}", key);
                Thread.currentThread().interrupt();
            } finally {
                activeProcesses.remove(key, process);
            }
        });
    }

    private void addVideoTranscodeArgs(
            List<String> command,
            MediaFile file,
            PlaybackProfile profile,
            TranscodePlan plan) {
        String scale = scaleFilter(file, profile.effectiveQuality());
        if (scale != null) {
            command.addAll(List.of("-vf", scale));
        }

        if (plan.strategy == EncodingStrategy.HARDWARE) {
            command.addAll(List.of("-c:v", hardwareEncoder()));
            int bitrate = targetVideoBitrateKbps(file, profile.effectiveQuality());
            command.addAll(videoBitrateArgs(bitrate));
            return;
        }

        command.addAll(List.of("-c:v", "libx264", "-preset", "veryfast"));
        if (profile.effectiveQuality().constrained()) {
            int bitrate = targetVideoBitrateKbps(file, profile.effectiveQuality());
            command.addAll(videoBitrateArgs(bitrate));
        } else {
            command.addAll(List.of("-crf", "23"));
        }
    }

    private List<String> videoBitrateArgs(int bitrateKbps) {
        return List.of(
                "-b:v", bitrateKbps + "k",
                "-maxrate", Math.round(bitrateKbps * 1.25f) + "k",
                "-bufsize", Math.round(bitrateKbps * 2.0f) + "k"
        );
    }

    private TranscodePlan buildTranscodePlan(MediaFile file, PlaybackProfile profile) {
        if (profile.transcodeMode() == TranscodeMode.HARDWARE) {
            return new TranscodePlan(EncodingStrategy.HARDWARE, hardwareEncoder());
        }
        if (profile.transcodeMode() == TranscodeMode.SOFTWARE) {
            return new TranscodePlan(EncodingStrategy.SOFTWARE, null);
        }
        if (profile.effectiveQuality().constrained()) {
            return new TranscodePlan(EncodingStrategy.SOFTWARE, null);
        }
        if (canStreamCopyHls(file)) {
            return new TranscodePlan(EncodingStrategy.STREAM_COPY, null);
        }
        return new TranscodePlan(EncodingStrategy.SOFTWARE, null);
    }

    private boolean isFreshPlaylist(Path playlist, Path source) {
        if (!Files.exists(playlist) || !Files.exists(source)) {
            return false;
        }
        try {
            return Files.size(playlist) > 0
                    && Files.getLastModifiedTime(playlist).toMillis() >= Files.getLastModifiedTime(source).toMillis();
        } catch (IOException e) {
            log.debug("Error checking HLS playlist freshness: {}", playlist, e);
            return false;
        }
    }

    private void waitForSegmentIfActive(Integer fileId, PlaybackProfile profile, Path segment) {
        if (Files.exists(segment)) {
            return;
        }
        Process activeProcess = activeProcesses.get(processKey(fileId, profile));
        if (activeProcess == null || !activeProcess.isAlive()) {
            return;
        }
        log.debug("Segment {} not ready yet, waiting for active FFmpeg process...", segment.getFileName());
        for (int i = 0; i < 50; i++) {
            sleepQuietly(100);
            if (Files.exists(segment) || !activeProcess.isAlive()) {
                break;
            }
        }
    }

    private boolean waitForPlaylist(Path playlist) {
        for (int i = 0; i < 50; i++) {
            if (Files.exists(playlist)) {
                try {
                    if (Files.size(playlist) > 0) {
                        return true;
                    }
                } catch (IOException ignored) {
                }
            }
            sleepQuietly(200);
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
                        return name.endsWith(".ts") || name.endsWith(".m3u8") || name.endsWith(".log");
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

    private PlaybackInfoResponse toPlaybackInfo(
            MediaFile file,
            PlaybackProfile profile,
            String mode,
            String playMethod,
            String url,
            boolean directPlayable,
            boolean transcoding,
            List<String> reasons) {
        return new PlaybackInfoResponse(
                mode,
                playMethod,
                url,
                mode.equals("hls") ? profile.variantKey() : null,
                profile.quality().value(),
                profile.transcodeMode().value(),
                directPlayable,
                transcoding,
                resolveContainer(file),
                file.getVideoCodec(),
                file.getAudioCodec(),
                file.getWidth(),
                file.getHeight(),
                file.getBitrate(),
                PlaybackQuality.optionsFor(file).stream()
                        .map(quality -> new PlaybackInfoResponse.PlaybackOption(
                                quality.value(), quality.label(), quality.maxHeight(), quality.videoBitrateKbps()))
                        .toList(),
                Arrays.stream(TranscodeMode.values())
                        .map(transcodeMode -> new PlaybackInfoResponse.PlaybackOption(
                                transcodeMode.value(), transcodeMode.label(), null, null))
                        .toList(),
                reasons
        );
    }

    private List<String> buildReasons(
            MediaFile file,
            PlaybackProfile profile,
            String mode,
            boolean directPlayable) {
        List<String> reasons = new ArrayList<>();
        if ("hls".equals(mode)) {
            reasons.add("PlaybackModeRequested");
        }
        if (profile.effectiveQuality().constrained()) {
            reasons.add("QualityRequested");
        }
        if (profile.transcodeMode() != TranscodeMode.AUTO) {
            reasons.add("TranscodeModeRequested");
        }
        if (!directPlayable) {
            String container = resolveContainer(file);
            if (!isDirectContainer(container, file)) {
                reasons.add("ContainerNotSupported");
            }
            if (!isBlank(file.getVideoCodec()) && !isDirectVideoCodec(container, file.getVideoCodec())) {
                reasons.add("VideoCodecNotSupported");
            }
            if (!isBlank(file.getAudioCodec()) && !isDirectAudioCodec(file.getAudioCodec())) {
                reasons.add("AudioCodecNotSupported");
            }
        }
        return reasons.stream().distinct().toList();
    }

    private boolean isDirectPlayable(MediaFile file) {
        String container = resolveContainer(file);
        if (!hasVideo(file)) {
            return isDirectAudioContainer(container) && (isBlank(file.getAudioCodec()) || isDirectAudioCodec(file.getAudioCodec()));
        }
        return isDirectContainer(container, file)
                && (isBlank(file.getVideoCodec()) || isDirectVideoCodec(container, file.getVideoCodec()))
                && (isBlank(file.getAudioCodec()) || isDirectAudioCodec(file.getAudioCodec()));
    }

    private boolean canStreamCopyHls(MediaFile file) {
        if (hasVideo(file) && !codecIn(file.getVideoCodec(), H264_CODECS)) {
            return false;
        }
        return isBlank(file.getAudioCodec()) || codecIn(file.getAudioCodec(), HLS_AUDIO_COPY_CODECS);
    }

    private boolean isDirectContainer(String container, MediaFile file) {
        if (hasVideo(file)) {
            return DIRECT_MP4_CONTAINERS.contains(container) || DIRECT_WEBM_CONTAINERS.contains(container);
        }
        return isDirectAudioContainer(container);
    }

    private boolean isDirectAudioContainer(String container) {
        return DIRECT_AUDIO_CONTAINERS.contains(container);
    }

    private boolean isDirectVideoCodec(String container, String codec) {
        if (DIRECT_WEBM_CONTAINERS.contains(container)) {
            return codecIn(codec, WEBM_VIDEO_CODECS);
        }
        return codecIn(codec, H264_CODECS);
    }

    private boolean isDirectAudioCodec(String codec) {
        return codecIn(codec, DIRECT_AUDIO_CODECS);
    }

    private boolean hasVideo(MediaFile file) {
        return !isBlank(file.getVideoCodec())
                || (file.getWidth() != null && file.getWidth() > 0)
                || (file.getMimeType() != null && file.getMimeType().toLowerCase(Locale.ROOT).startsWith("video/"));
    }

    private String scaleFilter(MediaFile file, PlaybackQuality quality) {
        if (!quality.constrained()) {
            return null;
        }
        Integer sourceHeight = file.getHeight();
        if (sourceHeight != null && sourceHeight > 0 && sourceHeight <= quality.maxHeight()) {
            return null;
        }
        return "scale=-2:" + quality.maxHeight();
    }

    private int targetVideoBitrateKbps(MediaFile file, PlaybackQuality quality) {
        if (quality.videoBitrateKbps() != null) {
            return quality.videoBitrateKbps();
        }
        Integer bitrate = file.getBitrate();
        if (bitrate != null && bitrate > 0) {
            return Math.max(700, Math.min(20000, bitrate / 1000));
        }
        Integer height = file.getHeight();
        if (height != null && height >= 2160) return 16000;
        if (height != null && height >= 1080) return 8000;
        if (height != null && height >= 720) return 4000;
        if (height != null && height >= 480) return 1800;
        return 900;
    }

    private String normalizePlaybackMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String mode = raw.trim().toLowerCase(Locale.ROOT);
        if ("raw".equals(mode) || "directplay".equals(mode)) {
            return "direct";
        }
        if ("transcode".equals(mode) || "hls".equals(mode) || "directstream".equals(mode)) {
            return "hls";
        }
        if ("direct".equals(mode) || "auto".equals(mode)) {
            return mode;
        }
        return "auto";
    }

    private String resolveContainer(MediaFile file) {
        if (file.getContainer() != null && !file.getContainer().isBlank()) {
            return normalizeToken(file.getContainer());
        }
        return guessContainer(file.getFilePath());
    }

    private String guessContainer(String path) {
        if (path == null) {
            return "";
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "";
        }
        return normalizeToken(path.substring(dot + 1));
    }

    private boolean codecIn(String raw, Set<String> allowed) {
        String codec = normalizeToken(raw);
        return allowed.contains(codec);
    }

    private String normalizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim().toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replace("_", "")
                .replace("-", "");
        if ("h265".equals(token) || "hevc".equals(token)) {
            return "hevc";
        }
        if ("mpeg4".equals(token)) {
            return "mpeg4";
        }
        return token;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Path profileDir(Integer fileId, PlaybackProfile profile) {
        return Path.of(cacheDir, "hls", String.valueOf(fileId), profile.variantKey());
    }

    private String processKey(Integer fileId, PlaybackProfile profile) {
        return fileId + ":" + profile.variantKey();
    }

    private void stopActiveProcess(String key) {
        Process process = activeProcesses.remove(key);
        if (process != null && process.isAlive()) {
            log.info("Terminating active FFmpeg process for {}", key);
            process.destroyForcibly();
        }
    }

    private Optional<String> resolveTelemetryKey(Integer fileId, String variant) {
        if (variant != null && !variant.isBlank()) {
            PlaybackProfile profile = PlaybackProfile.fromVariant(variant);
            return Optional.of(processKey(fileId, profile));
        }
        return activeProcesses.keySet().stream()
                .filter(key -> key.startsWith(fileId + ":"))
                .findFirst();
    }

    private String variantFromProcessKey(String key) {
        int idx = key.indexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : new PlaybackProfile(PlaybackQuality.AUTO, TranscodeMode.AUTO).variantKey();
    }

    private Path latestLogPath(Integer fileId, String variant) {
        Path dir = Path.of(cacheDir, "hls", String.valueOf(fileId), variant);
        List<Path> candidates = List.of(
                dir.resolve("ffmpeg-hardware.log"),
                dir.resolve("ffmpeg-transcode.log"),
                dir.resolve("ffmpeg-copy.log")
        );
        return candidates.stream()
                .filter(Files::exists)
                .max((left, right) -> {
                    try {
                        return Files.getLastModifiedTime(left).compareTo(Files.getLastModifiedTime(right));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);
    }

    private String hardwareEncoder() {
        return sysConfigService.getString("playback.hardware_encoder", yamlHardwareEncoder).trim();
    }

    private double parseFfmpegDouble(String line, String token, double fallback) {
        Optional<String> value = parseFfmpegToken(line, token);
        if (value.isEmpty()) {
            return fallback;
        }
        String normalized = value.get().replace("x", "").trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Optional<String> parseFfmpegToken(String line, String token) {
        int idx = line.indexOf(token);
        if (idx < 0) {
            return Optional.empty();
        }
        String rest = line.substring(idx + token.length()).trim();
        if (rest.isEmpty()) {
            return Optional.empty();
        }
        int end = rest.indexOf(' ');
        int tab = rest.indexOf('\t');
        if (end < 0 || (tab >= 0 && tab < end)) {
            end = tab;
        }
        if (end < 0) {
            end = rest.length();
        }
        return Optional.of(rest.substring(0, end).trim());
    }

    private List<String> tailReadLines(Path filePath, int maxLines) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return List.of();
            }

            int readSize = (int) Math.min(fileLength, 64 * 1024);
            byte[] buffer = new byte[readSize];
            raf.seek(fileLength - readSize);
            raf.readFully(buffer);

            String tail = new String(buffer, StandardCharsets.UTF_8);
            String[] allLines = tail.split("\n");

            int start = Math.max(0, allLines.length - maxLines);
            if (fileLength > readSize && start == 0) {
                start = 1;
            }
            return List.of(Arrays.copyOfRange(allLines, start, allLines.length));
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Set<Integer> getActiveStreamingFileIds() {
        return activeProcesses.keySet().stream()
                .map(key -> key.split(":", 2)[0])
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private enum EncodingStrategy {
        STREAM_COPY,
        SOFTWARE,
        HARDWARE
    }

    private record TranscodePlan(EncodingStrategy strategy, String encoder) {
        private String logFileName() {
            return switch (strategy) {
                case STREAM_COPY -> "ffmpeg-copy.log";
                case SOFTWARE -> "ffmpeg-transcode.log";
                case HARDWARE -> "ffmpeg-hardware.log";
            };
        }
    }
}
