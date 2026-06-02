package com.mediamanager.streaming.job;

import com.mediamanager.streaming.service.HlsStreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class HlsCacheCleanupJob {

    private final HlsStreamingService hlsStreamingService;

    @Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupStaleHls() {
        int hlsRemoved = cleanupHlsRoot(Path.of(cacheDir, "hls"), 7);
        int imageRemoved = cleanupCacheRoot(Path.of(cacheDir, "images"), 30);
        if (hlsRemoved > 0 || imageRemoved > 0) {
            log.info("Cache cleanup removed {} HLS directories and {} image directories", hlsRemoved, imageRemoved);
        }
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupStaleHlsHourly() {
        Path hlsRoot = Path.of(cacheDir, "hls");
        if (!Files.exists(hlsRoot)) {
            return;
        }
        java.util.Set<Integer> activeFileIds = hlsStreamingService.getActiveStreamingFileIds();
        Instant cutoff = Instant.now().minus(2, ChronoUnit.HOURS);
        int segmentsRemoved = 0;
        int emptyDirsRemoved = 0;
        try (Stream<Path> dirs = Files.list(hlsRoot)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                
                try {
                    Integer fileId = Integer.parseInt(dir.getFileName().toString());
                    if (activeFileIds.contains(fileId)) {
                        log.debug("Skipping hourly segment cleanup for active streaming file {}", fileId);
                        continue;
                    }
                } catch (NumberFormatException ignored) {}

                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : files.toList()) {
                        if (!Files.isDirectory(file) && file.toString().endsWith(".ts")) {
                            Instant lastModified = Files.getLastModifiedTime(file).toInstant();
                            if (lastModified.isBefore(cutoff)) {
                                Files.deleteIfExists(file);
                                segmentsRemoved++;
                            }
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to clean segments in {}: {}", dir, e.getMessage());
                }

                try {
                    boolean isEmpty;
                    try (Stream<Path> stream = Files.list(dir)) {
                        isEmpty = !stream.findAny().isPresent();
                    }
                    if (isEmpty) {
                        Files.deleteIfExists(dir);
                        emptyDirsRemoved++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to check if empty/delete dir {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Hourly HLS cache cleanup failed: {}", e.getMessage());
        }
        if (segmentsRemoved > 0 || emptyDirsRemoved > 0) {
            log.info("Hourly HLS cleanup removed {} stale segments and {} empty HLS directories", segmentsRemoved, emptyDirsRemoved);
        }
    }

    private int cleanupHlsRoot(Path root, int maxAgeDays) {
        if (!Files.exists(root)) {
            return 0;
        }
        java.util.Set<Integer> activeFileIds = hlsStreamingService.getActiveStreamingFileIds();
        Instant cutoff = Instant.now().minus(maxAgeDays, ChronoUnit.DAYS);
        int removed = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                
                try {
                    Integer fileId = Integer.parseInt(dir.getFileName().toString());
                    if (activeFileIds.contains(fileId)) {
                        log.debug("Skipping HLS root cleanup for active streaming file {}", fileId);
                        continue;
                    }
                } catch (NumberFormatException ignored) {}

                try {
                    Instant lastModified = latestModifiedTime(dir);
                    if (lastModified.isBefore(cutoff)) {
                        deleteRecursive(dir);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to inspect cache dir {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Cache cleanup failed for {}: {}", root, e.getMessage());
        }
        return removed;
    }

    private int cleanupCacheRoot(Path root, int maxAgeDays) {
        if (!Files.exists(root)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(maxAgeDays, ChronoUnit.DAYS);
        int removed = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try {
                    Instant lastModified = latestModifiedTime(dir);
                    if (lastModified.isBefore(cutoff)) {
                        deleteRecursive(dir);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to inspect cache dir {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Cache cleanup failed for {}: {}", root, e.getMessage());
        }
        return removed;
    }

    private Instant latestModifiedTime(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Instant.EPOCH;
        }
        Instant latest = Files.getLastModifiedTime(path).toInstant();
        if (!Files.isDirectory(path)) {
            return latest;
        }
        try (Stream<Path> children = Files.list(path)) {
            for (Path child : children.toList()) {
                Instant childTime = latestModifiedTime(child);
                if (childTime.isAfter(latest)) {
                    latest = childTime;
                }
            }
        }
        return latest;
    }

    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                for (Path child : children.toList()) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
