package com.mediamanager.sync.job;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.library.service.LibraryScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledScanJob {

    private final MediaLibraryRepository libraryRepository;
    private final LibraryScanService scanService;

    // Run every day at 3 AM
    @Scheduled(cron = "0 0 3 * * ?")
    public void executeDailyFullScan() {
        log.info("Executing daily scheduled full library scan.");
        List<MediaLibrary> libraries = libraryRepository.findAll();
        for (MediaLibrary library : libraries) {
            if (!Boolean.TRUE.equals(library.getAutoScan())) {
                log.debug("Skipping library '{}' (autoScan disabled)", library.getName());
                continue;
            }
            scanService.scanLibraryAsync(library.getId());
        }
    }

    // Run every hour to check for libraries that haven't been scanned in 24h
    @Scheduled(fixedRate = 3600000)
    public void executeCatchupScan() {
        log.debug("Checking for libraries overdue for scan...");
        Instant overdueBoundary = Instant.now().minus(24, ChronoUnit.HOURS);
        
        libraryRepository.findAll().stream()
            .filter(lib -> Boolean.TRUE.equals(lib.getAutoScan()))
            .filter(lib -> lib.getLastScannedAt() == null || lib.getLastScannedAt().isBefore(overdueBoundary))
            .forEach(lib -> {
                log.info("Library '{}' is overdue for scan. Triggering now.", lib.getName());
                scanService.scanLibraryAsync(lib.getId());
            });
    }
}
