package com.mediamanager.metadata.job;

import com.mediamanager.metadata.entity.ScrapeSchedule;
import com.mediamanager.metadata.repository.ScrapeScheduleRepository;
import com.mediamanager.metadata.service.ScrapeScheduleService;
import com.mediamanager.metadata.service.ScrapeTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScrapeScheduler {

    private final ScrapeScheduleRepository scheduleRepository;
    private final ScrapeScheduleService scheduleService;
    private final ScrapeTaskService taskService;

    // In-memory concurrency limiter per schedule id (single-instance deployment)
    private final Map<Integer, Semaphore> scheduleSemaphores = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${mediamanager.scraper.scheduler-poll-ms:5000}")
    public void tick() {
        Instant now = Instant.now();
        List<ScrapeSchedule> due = scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(now);
        if (due.isEmpty()) return;

        for (ScrapeSchedule schedule : due) {
            tryRunSchedule(schedule, now);
        }
    }

    private void tryRunSchedule(ScrapeSchedule schedule, Instant now) {
        Integer scheduleId = schedule.getId();
        if (scheduleId == null) return;

        Semaphore sem = scheduleSemaphores.computeIfAbsent(scheduleId, id ->
                new Semaphore(Math.max(1, schedule.getMaxConcurrency() != null ? schedule.getMaxConcurrency() : 1)));

        if (!sem.tryAcquire()) {
            log.debug("Schedule {} is at max concurrency, skipping this tick", scheduleId);
            return;
        }

        try {
            claimAndDispatch(scheduleId, now);
        } finally {
            sem.release();
        }
    }

    @Transactional
    protected void claimAndDispatch(Integer scheduleId, Instant now) {
        // Re-load within transaction to avoid stale data
        ScrapeSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) return;
        if (!Boolean.TRUE.equals(schedule.getEnabled())) return;
        if (schedule.getNextRunAt() == null || schedule.getNextRunAt().isAfter(now)) return;

        // Claim by advancing next_run_at first (prevents tight re-entry in same instance/thread)
        Instant next = scheduleService.computeNextRunAt(schedule, now);
        schedule.setLastRunAt(now);
        schedule.setNextRunAt(next);
        schedule.setLastStatus("TRIGGERED");
        schedule.setLastError(null);
        scheduleRepository.save(schedule);

        Integer libraryId = schedule.getLibrary() != null ? schedule.getLibrary().getId() : null;
        String paramsJson = scheduleService.buildTaskParamsJson(schedule);

        taskService.startScrapeWithParams(
                schedule.getId().longValue(),
                libraryId,
                "SCHEDULED",
                schedule.getTargetStatus(),
                schedule.getMediaTypes(),
                paramsJson
        );
    }
}

