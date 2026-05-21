package com.mediamanager.streaming.job;

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
public class HlsCacheCleanupJob {

    @Value("${mediamanager.data.cache-dir:./data/cache}")
    private String cacheDir;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupStaleHls() {
        Path hlsRoot = Path.of(cacheDir, "hls");
        if (!Files.exists(hlsRoot)) {
            return;
        }
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int removed = 0;
        try (Stream<Path> dirs = Files.list(hlsRoot)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try {
                    Instant lastModified = Files.getLastModifiedTime(dir).toInstant();
                    if (lastModified.isBefore(cutoff)) {
                        deleteRecursive(dir);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to inspect HLS cache dir {}: {}", dir, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("HLS cache cleanup failed: {}", e.getMessage());
        }
        if (removed > 0) {
            log.info("HLS cache cleanup removed {} stale directories", removed);
        }
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
