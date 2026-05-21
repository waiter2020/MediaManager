package com.mediamanager.media.job;

import com.mediamanager.media.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleBinCleanupJob {

    private final RecycleBinService recycleBinService;

    @Scheduled(cron = "0 30 2 * * ?")
    public void purgeOldDeletedFiles() {
        Instant before = Instant.now().minus(30, ChronoUnit.DAYS);
        int removed = recycleBinService.purgeExpired(before);
        if (removed > 0) {
            log.info("Recycle bin cleanup removed {} expired file records", removed);
        }
    }
}
