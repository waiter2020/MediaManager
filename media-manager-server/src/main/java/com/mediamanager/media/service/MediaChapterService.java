package com.mediamanager.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.dto.MediaChapterDto;
import com.mediamanager.media.entity.MediaChapter;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaChapterRepository;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaChapterService {

    private static final int MAX_EMBEDDED_CHAPTERS = 48;
    private static final int MAX_GENERATED_CHAPTERS = 12;
    private static final double GENERATED_CHAPTER_TARGET_SECONDS = 300.0;

    private final MediaChapterRepository chapterRepository;
    private final MediaFileRepository fileRepository;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final StoragePathMapper storagePathMapper;
    private final LibraryAccessService libraryAccessService;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    @Value("${mediamanager.ffprobe.path:ffprobe}")
    private String yamlFfprobePath;

    @Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    public void ensureChaptersForItem(MediaItem item) {
        if (item == null || item.getId() == null || !isVideoItem(item)) {
            return;
        }
        fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .filter(file -> file.getVideoCodec() != null || looksLikeVideo(file.getContainer()))
                .findFirst()
                .ifPresent(this::ensureChaptersForFile);
    }

    @Transactional(readOnly = true)
    public boolean needsChaptersForFile(MediaFile file) {
        if (file == null || file.getId() == null || !isVideoFile(file)) {
            return false;
        }
        return !chapterRepository.existsByMediaFileId(file.getId());
    }

    @Async("postProcessExecutor")
    public void ensureChaptersForFileAsync(Integer fileId) {
        if (fileId == null) {
            return;
        }
        fileRepository.findById(fileId).ifPresent(this::ensureChaptersForFile);
    }

    @Transactional(readOnly = true)
    public List<MediaChapterDto> getChaptersForItem(Integer itemId) {
        return chapterRepository.findActiveByMediaItemId(itemId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getChapterThumbnail(Integer chapterId) {
        MediaChapter chapter = chapterRepository.findByIdWithFileAndLibrary(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        libraryAccessService.assertCanViewFile(chapter.getMediaFile());
        if (chapter.getThumbnailPath() == null || chapter.getThumbnailPath().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND, "Chapter thumbnail not found");
        }

        Path path = Path.of(chapter.getThumbnailPath());
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND, "Chapter thumbnail not found");
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(resource);
    }

    private void ensureChaptersForFile(MediaFile file) {
        try {
            Path source = Path.of(storagePathMapper.mapPathIfNeeded(file.getFilePath()));
            if (!Files.exists(source) || !Files.isReadable(source)) {
                log.debug("Skipping chapter extraction for unreadable file {}", file.getFilePath());
                return;
            }

            List<MediaChapter> existing = chapterRepository.findByMediaFileIdOrderByChapterIndexAsc(file.getId());
            if (chaptersAreFresh(existing, source)) {
                return;
            }

            ProbeResult probe = probeChapters(source);
            if (probe == null) {
                return;
            }
            List<ChapterCandidate> candidates = probe.chapters().isEmpty()
                    ? generateIntervalChapters(probe.durationSeconds())
                    : probe.chapters();
            if (candidates.isEmpty()) {
                return;
            }

            List<MediaChapter> chapters = new ArrayList<>(candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                ChapterCandidate candidate = candidates.get(i);
                String thumbnailPath = generateChapterThumbnail(
                        file.getId(), i, source, candidate.startSeconds(), probe.durationSeconds());
                chapters.add(MediaChapter.builder()
                        .mediaFile(file)
                        .chapterIndex(i)
                        .title(candidate.title())
                        .startSeconds(candidate.startSeconds())
                        .endSeconds(candidate.endSeconds())
                        .source(candidate.source())
                        .thumbnailPath(thumbnailPath)
                        .build());
            }

            chapterRepository.deleteByMediaFileId(file.getId());
            chapterRepository.saveAll(chapters);
            deleteStaleThumbnails(file.getId(), chapters);
            log.info("Extracted {} chapters for media file {}", chapters.size(), file.getId());
        } catch (Exception e) {
            log.warn("Chapter extraction failed for media file {}: {}", file.getId(), e.getMessage());
        }
    }

    private ProbeResult probeChapters(Path source) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                sysConfigService.ffprobePath(yamlFfprobePath),
                "-v", "error",
                "-print_format", "json",
                "-show_chapters",
                "-show_format",
                "-show_entries", "format=duration:chapter=start_time,end_time:chapter_tags=title",
                source.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        FutureTask<byte[]> outputTask = new FutureTask<>(() -> process.getInputStream().readAllBytes());
        Thread.ofVirtual().name("chapter-ffprobe-output").start(outputTask);
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            outputTask.cancel(true);
            log.warn("ffprobe chapter extraction timed out for {}", source);
            return null;
        }

        byte[] output;
        try {
            output = outputTask.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            outputTask.cancel(true);
            log.warn("Failed to read ffprobe chapter output for {}: {}", source, e.getMessage());
            return null;
        }
        if (process.exitValue() != 0) {
            log.warn("ffprobe chapter extraction exited with code {} for {}", process.exitValue(), source);
            return null;
        }
        JsonNode root = objectMapper.readTree(output);
        double duration = root.path("format").path("duration").asDouble(0);
        List<ChapterCandidate> chapters = parseEmbeddedChapters(root.path("chapters"), duration);
        return new ProbeResult(chapters, duration);
    }

    private List<ChapterCandidate> parseEmbeddedChapters(JsonNode chaptersNode, double duration) {
        if (!chaptersNode.isArray()) {
            return List.of();
        }
        List<ChapterCandidate> chapters = new ArrayList<>();
        for (JsonNode node : chaptersNode) {
            double start = node.path("start_time").asDouble(-1);
            double end = node.path("end_time").asDouble(duration > 0 ? duration : -1);
            if (start < 0 || (duration > 0 && start >= duration)) {
                continue;
            }
            String title = node.path("tags").path("title").asText(null);
            chapters.add(new ChapterCandidate(
                    title,
                    start,
                    end > start ? end : null,
                    "EMBEDDED"
            ));
        }
        chapters.sort(Comparator.comparingDouble(ChapterCandidate::startSeconds));
        if (chapters.size() > MAX_EMBEDDED_CHAPTERS) {
            chapters = new ArrayList<>(chapters.subList(0, MAX_EMBEDDED_CHAPTERS));
        }
        for (int i = 0; i < chapters.size(); i++) {
            ChapterCandidate chapter = chapters.get(i);
            if (chapter.title() == null || chapter.title().isBlank()) {
                chapters.set(i, new ChapterCandidate(
                        chapterTitle(i),
                        chapter.startSeconds(),
                        chapter.endSeconds(),
                        chapter.source()
                ));
            }
        }
        return chapters;
    }

    private List<ChapterCandidate> generateIntervalChapters(double duration) {
        if (duration <= 0) {
            return List.of();
        }
        int count = Math.min(
                MAX_GENERATED_CHAPTERS,
                Math.max(1, (int) Math.ceil(duration / GENERATED_CHAPTER_TARGET_SECONDS))
        );
        double interval = duration / count;
        List<ChapterCandidate> chapters = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double start = Math.floor(i * interval);
            double end = i == count - 1 ? duration : Math.floor((i + 1) * interval);
            chapters.add(new ChapterCandidate(chapterTitle(i), start, end, "GENERATED"));
        }
        return chapters;
    }

    private String generateChapterThumbnail(
            Integer fileId,
            int chapterIndex,
            Path source,
            double startSeconds,
            double durationSeconds) {
        try {
            Path outDir = Path.of(cacheDir, "chapters", String.valueOf(fileId));
            Files.createDirectories(outDir);
            Path output = outDir.resolve(String.format(Locale.ROOT, "%03d.jpg", chapterIndex)).toAbsolutePath();
            if (Files.exists(output)
                    && Files.getLastModifiedTime(output).toMillis() >= Files.getLastModifiedTime(source).toMillis()) {
                return output.toString();
            }

            double seek = Math.max(0, startSeconds + 0.5);
            if (durationSeconds > 1) {
                seek = Math.min(seek, durationSeconds - 0.5);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    sysConfigService.ffmpegPath(yamlFfmpegPath),
                    "-hide_banner",
                    "-loglevel", "error",
                    "-ss", String.format(Locale.ROOT, "%.3f", seek),
                    "-i", source.toString(),
                    "-frames:v", "1",
                    "-vf", "scale=480:-2",
                    "-q:v", "5",
                    "-y",
                    output.toString()
            );
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Chapter thumbnail generation timed out for file {} chapter {}", fileId, chapterIndex);
                return null;
            }
            return process.exitValue() == 0 && Files.exists(output) && Files.size(output) > 0
                    ? output.toString()
                    : null;
        } catch (Exception e) {
            log.debug("Chapter thumbnail generation failed for file {} chapter {}: {}",
                    fileId, chapterIndex, e.getMessage());
            return null;
        }
    }

    private void deleteStaleThumbnails(Integer fileId, List<MediaChapter> chapters) {
        Path outDir = Path.of(cacheDir, "chapters", String.valueOf(fileId));
        if (!Files.isDirectory(outDir)) {
            return;
        }
        Set<Path> retained = chapters.stream()
                .map(MediaChapter::getThumbnailPath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize())
                .collect(Collectors.toSet());
        try (Stream<Path> files = Files.list(outDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> !retained.contains(path.toAbsolutePath().normalize()))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Failed to remove stale chapter thumbnail {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.debug("Failed to clean stale chapter thumbnails for file {}: {}", fileId, e.getMessage());
        }
    }

    private boolean chaptersAreFresh(List<MediaChapter> chapters, Path source) {
        if (chapters.isEmpty()) {
            return false;
        }
        try {
            long sourceModified = Files.getLastModifiedTime(source).toMillis();
            return chapters.stream().allMatch(chapter -> {
                if (chapter.getThumbnailPath() == null || chapter.getThumbnailPath().isBlank()) {
                    return false;
                }
                Path thumbnail = Path.of(chapter.getThumbnailPath());
                try {
                    return Files.exists(thumbnail)
                            && Files.getLastModifiedTime(thumbnail).toMillis() >= sourceModified;
                } catch (IOException e) {
                    return false;
                }
            });
        } catch (IOException e) {
            return false;
        }
    }

    private MediaChapterDto toDto(MediaChapter chapter) {
        boolean thumbnailAvailable = chapter.getThumbnailPath() != null
                && !chapter.getThumbnailPath().isBlank()
                && Files.exists(Path.of(chapter.getThumbnailPath()));
        return MediaChapterDto.builder()
                .id(chapter.getId())
                .mediaFileId(chapter.getMediaFile().getId())
                .chapterIndex(chapter.getChapterIndex())
                .title(chapter.getTitle())
                .startSeconds(chapter.getStartSeconds())
                .endSeconds(chapter.getEndSeconds())
                .source(chapter.getSource())
                .thumbnailAvailable(thumbnailAvailable)
                .build();
    }

    private static boolean isVideoItem(MediaItem item) {
        return "MOVIE".equals(item.getType()) || "TV_SHOW".equals(item.getType()) || "EPISODE".equals(item.getType());
    }

    private static boolean isVideoFile(MediaFile file) {
        return file.getVideoCodec() != null
                || looksLikeVideo(file.getContainer())
                || looksLikeVideo(fileExtension(file.getFileName()));
    }

    private static boolean looksLikeVideo(String container) {
        if (container == null) {
            return false;
        }
        return switch (container.toLowerCase(Locale.ROOT)) {
            case "3gp", "asf", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4",
                    "mpg", "mpeg", "mts", "ogv", "ts", "vob", "webm", "wmv" -> true;
            default -> false;
        };
    }

    private static String fileExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex + 1) : null;
    }

    private static String chapterTitle(int index) {
        return String.format(Locale.ROOT, "章节 %02d", index + 1);
    }

    private record ChapterCandidate(String title, double startSeconds, Double endSeconds, String source) {
    }

    private record ProbeResult(List<ChapterCandidate> chapters, double durationSeconds) {
    }
}
