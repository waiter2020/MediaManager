package com.mediamanager.metadata.job;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.metadata.service.ScrapeTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job that automatically triggers metadata scraping
 * for all libraries with autoScan enabled.
 *
 * By default runs daily at 4 AM. Configurable via:
 *   mediamanager.scraper.cron in application.yml
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledScrapeJob {

    private final MediaLibraryRepository libraryRepository;
    private final ScrapeTaskService scrapeTaskService;

    /**
     * Daily scheduled scrape: targets UNIDENTIFIED items across all auto-scan libraries.
     */
    @Scheduled(cron = "${mediamanager.scraper.cron:0 0 4 * * ?}")
    public void executeDailyScrape() {
        log.info("Executing daily scheduled metadata scrape.");

        List<MediaLibrary> libraries = libraryRepository.findAll();

        for (MediaLibrary library : libraries) {
            if (!Boolean.TRUE.equals(library.getAutoScan())) {
                log.debug("Skipping library '{}' (autoScan disabled)", library.getName());
                continue;
            }

            log.info("Triggering scheduled scrape for library '{}'", library.getName());
            scrapeTaskService.startScrape(library.getId(), "SCHEDULED", "UNIDENTIFIED");
        }
    }
}
