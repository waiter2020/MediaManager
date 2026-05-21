package com.mediamanager.metadata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.MediaPostProcessService;
import com.mediamanager.metadata.dto.ScrapeTaskResponse;
import com.mediamanager.metadata.entity.ScrapeTask;
import com.mediamanager.metadata.repository.ScrapeTaskRepository;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.util.FileNameParser;
import com.mediamanager.sync.service.SseService;
import com.mediamanager.system.dto.SystemLogEventDto;
import com.mediamanager.system.service.SystemLogBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeTaskService {

    private final ScrapeTaskRepository scrapeTaskRepository;
    private final MediaItemRepository mediaItemRepository;
    private final MediaFileRepository mediaFileRepository;
    private final MediaLibraryRepository libraryRepository;
    private final MetadataPipelineService pipelineService;
    private final MetadataApplyService metadataApplyService;
    private final FileNameParser fileNameParser;
    private final MediaPostProcessService mediaPostProcessService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    @Value("${mediamanager.scraper.request-delay-ms:2000}")
    private long requestDelayMs;

    @Value("${mediamanager.scraper.batch-size:50}")
    private int batchSize;

    /** Track cancelled task IDs so async thread can check */
    private final Map<Integer, Boolean> cancelledTasks = new ConcurrentHashMap<>();

    /** Basic library-level mutex to avoid hammering same library concurrently (single-instance). */
    private final Map<Integer, Object> libraryLocks = new ConcurrentHashMap<>();

    // ── Public API ──────────────────────────────────────────

    /**
     * Start a scrape task.
     *
     * @param libraryId   optional, null = all libraries
     * @param triggerType MANUAL or SCHEDULED
     * @param targetStatus which items to scrape: "UNIDENTIFIED", "IDENTIFIED", or "ALL"
     */
    @Transactional
    public ScrapeTaskResponse startScrape(Integer libraryId, String triggerType, String targetStatus) {
        return startScrapeWithParams(null, libraryId, triggerType, targetStatus, null, null);
    }

    /**
     * Start a scrape task with snapshot parameters.
     *
     * @param scheduleId optional, null for manual runs
     * @param libraryId optional, null = all libraries
     * @param triggerType MANUAL or SCHEDULED
     * @param targetStatus UNIDENTIFIED | IDENTIFIED | ALL
     * @param mediaTypesJson optional JSON array string, e.g. ["MOVIE","TV_SHOW"]
     * @param paramsJson optional JSON object string for overrides, e.g. {"requestDelayMs":2000,"batchSize":50}
     */
    public ScrapeTaskResponse startScrapeWithParams(
            Long scheduleId,
            Integer libraryId,
            String triggerType,
            String targetStatus,
            String mediaTypesJson,
            String paramsJson
    ) {
        int attempts = 0;
        while (true) {
            try {
                return startScrapeWithParamsOnce(scheduleId, libraryId, triggerType, targetStatus, mediaTypesJson, paramsJson);
            } catch (CannotAcquireLockException e) {
                attempts++;
                if (attempts >= 5) {
                    throw e;
                }
                try {
                    Thread.sleep(200L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected ScrapeTaskResponse startScrapeWithParamsOnce(
            Long scheduleId,
            Integer libraryId,
            String triggerType,
            String targetStatus,
            String mediaTypesJson,
            String paramsJson
    ) {

        MediaLibrary library = null;
        if (libraryId != null) {
            library = libraryRepository.findById(libraryId).orElse(null);
            if (library == null) {
                log.error("Library {} not found for scrape", libraryId);
                return null;
            }
        }

        String effectiveTarget = (targetStatus != null) ? targetStatus : "UNIDENTIFIED";
        String effectiveMediaTypesJson = (mediaTypesJson != null && !mediaTypesJson.isBlank()) ? mediaTypesJson : null;

        // Count items to scrape
        List<MediaItem> items = findItemsByTarget(libraryId, effectiveTarget, effectiveMediaTypesJson);
        if (items.isEmpty()) {
            log.info("No items to scrape (target={}){}" , effectiveTarget,
                    libraryId != null ? " in library " + libraryId : "");
            return null;
        }

        ScrapeTask task = ScrapeTask.builder()
                .scheduleId(scheduleId)
                .library(library)
                .status("PENDING")
                .triggerType(triggerType != null ? triggerType : "MANUAL")
                .targetStatus(effectiveTarget)
                .mediaTypes(effectiveMediaTypesJson)
                .paramsJson((paramsJson != null && !paramsJson.isBlank()) ? paramsJson : null)
                .totalItems(items.size())
                .build();
        scrapeTaskRepository.save(task);

        log.info("Created scrape task {} for {} items", task.getId(), items.size());
        broadcastLog("INFO", "SCRAPE_CREATED",
                libraryId, "Created scrape task for " + items.size() + " items");

        // Launch async execution
        executeScrapeAsync(task.getId());

        return toResponse(task);
    }

    @Transactional
    public boolean cancelTask(Integer taskId) {
        return scrapeTaskRepository.findById(taskId).map(task -> {
            if ("RUNNING".equals(task.getStatus()) || "PENDING".equals(task.getStatus())) {
                cancelledTasks.put(taskId, true);
                task.setStatus("CANCELLED");
                task.setFinishedAt(Instant.now());
                scrapeTaskRepository.save(task);
                log.info("Cancelled scrape task {}", taskId);
                return true;
            }
            return false;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ScrapeTaskResponse> getAllTasks() {
        return scrapeTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScrapeTaskResponse> getTasksByScheduleId(Long scheduleId) {
        return scrapeTaskRepository.findByScheduleIdOrderByCreatedAtDesc(scheduleId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScrapeTaskResponse getTask(Integer taskId) {
        return scrapeTaskRepository.findById(taskId)
                .map(this::toResponse)
                .orElse(null);
    }

    // ── Async Execution ─────────────────────────────────────

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeScrapeAsync(Integer taskId) {
        ScrapeTask task = scrapeTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        task.setStatus("RUNNING");
        task.setStartedAt(Instant.now());
        scrapeTaskRepository.save(task);

        sseService.broadcast("scrape-start", "Scrape task " + taskId + " started");
        broadcastLog("INFO", "SCRAPE_START",
                task.getLibrary() != null ? task.getLibrary().getId() : null,
                "Started scrape task " + taskId);

        Integer libraryId = task.getLibrary() != null ? task.getLibrary().getId() : null;
        String effectiveTarget = task.getTargetStatus() != null ? task.getTargetStatus() : "UNIDENTIFIED";
        String effectiveMediaTypesJson = (task.getMediaTypes() != null && !task.getMediaTypes().isBlank())
                ? task.getMediaTypes()
                : null;

        // Re-query by the task snapshot to keep semantics consistent.
        List<MediaItem> items = findItemsByTarget(libraryId, effectiveTarget, effectiveMediaTypesJson);

        int scraped = 0;
        int errors = 0;
        StringBuilder errorLog = new StringBuilder();

        Object lock = libraryId != null ? libraryLocks.computeIfAbsent(libraryId, k -> new Object()) : new Object();
        synchronized (lock) {
            for (MediaItem item : items) {
                // Check cancellation
                if (cancelledTasks.remove(taskId) != null) {
                    log.info("Scrape task {} was cancelled after {} items", taskId, scraped);
                    break;
                }

                try {
                    scrapeItem(item);
                    scraped++;
                } catch (Exception e) {
                    errors++;
                    String msg = String.format("Item %d (%s): %s\n", item.getId(), item.getTitle(), e.getMessage());
                    errorLog.append(msg);
                    log.error("Failed to scrape item {}", item.getId(), e);
                }

                // Update progress
                updateTaskProgress(taskId, scraped, errors, errorLog.toString());

                // Rate limiting (can be overridden by params_json in future)
                long effectiveDelayMs = requestDelayMs;
                if (effectiveDelayMs > 0) {
                    try {
                        Thread.sleep(effectiveDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Finalize
        finalizeTask(taskId, scraped, errors, errorLog.toString());
    }

    // ── Item-level scraping ─────────────────────────────────

    private void scrapeItem(MediaItem item) {
        List<MediaFile> files = mediaFileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);

        MetadataResult pipelineResult = pipelineService.executeScrapePipeline(item, primaryFile);
        if (pipelineResult.getTitle() == null && primaryFile != null) {
            MetadataResult fallback = fileNameParser.parse(primaryFile.getFileName());
            pipelineResult.mergeFrom(fallback);
        }
        metadataApplyService.applyResult(item, pipelineResult, primaryFile);
        mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
    }

    // ── Progress helpers ────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTaskProgress(Integer taskId, int scraped, int errors, String errorLog) {
        scrapeTaskRepository.findById(taskId).ifPresent(task -> {
            task.setScrapedItems(scraped);
            task.setErrorItems(errors);
            if (errorLog != null && !errorLog.isEmpty()) task.setErrorLog(errorLog);
            scrapeTaskRepository.save(task);

            // SSE broadcast progress
            sseService.broadcast("scrape-progress", Map.of(
                    "taskId", taskId,
                    "scraped", scraped,
                    "errors", errors,
                    "total", task.getTotalItems()
            ));
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeTask(Integer taskId, int scraped, int errors, String errorLog) {
        scrapeTaskRepository.findById(taskId).ifPresent(task -> {
            if (!"CANCELLED".equals(task.getStatus())) {
                task.setStatus(errors > 0 && scraped == 0 ? "FAILED" : "SUCCESS");
            }
            task.setScrapedItems(scraped);
            task.setErrorItems(errors);
            if (errorLog != null && !errorLog.isEmpty()) task.setErrorLog(errorLog);
            task.setFinishedAt(Instant.now());
            scrapeTaskRepository.save(task);

            String summary = String.format("Scrape task %d finished: %d scraped, %d errors",
                    taskId, scraped, errors);
            log.info(summary);
            sseService.broadcast("scrape.task.updated", Map.of(
                    "taskId", taskId,
                    "status", task.getStatus(),
                    "scraped", scraped,
                    "errors", errors));
            sseService.broadcast("scrape-end", summary);
            broadcastLog("INFO", "SCRAPE_DONE",
                    task.getLibrary() != null ? task.getLibrary().getId() : null, summary);
        });
    }

    // ── Helpers ──────────────────────────────────────────────

    private List<MediaItem> findItemsByTarget(Integer libraryId, String targetStatus, String mediaTypesJson) {
        Set<String> mediaTypes = parseMediaTypes(mediaTypesJson);

        if ("ALL".equalsIgnoreCase(targetStatus)) {
            List<MediaItem> items = (libraryId != null)
                    ? mediaItemRepository.findByLibraryIdOrderByIdAsc(libraryId)
                    : mediaItemRepository.findAllByOrderByIdAsc();
            return filterByMediaTypes(items, mediaTypes);
        }
        // UNIDENTIFIED or IDENTIFIED
        List<MediaItem> items = (libraryId != null)
                ? mediaItemRepository.findByLibraryIdAndStatusOrderByIdAsc(libraryId, targetStatus)
                : mediaItemRepository.findByStatusOrderByIdAsc(targetStatus);
        return filterByMediaTypes(items, mediaTypes);
    }

    private ScrapeTaskResponse toResponse(ScrapeTask task) {
        return ScrapeTaskResponse.builder()
                .id(task.getId())
                .scheduleId(task.getScheduleId())
                .libraryId(task.getLibrary() != null ? task.getLibrary().getId() : null)
                .libraryName(task.getLibrary() != null ? task.getLibrary().getName() : null)
                .status(task.getStatus())
                .triggerType(task.getTriggerType())
                .targetStatus(task.getTargetStatus())
                .mediaTypes(task.getMediaTypes())
                .totalItems(task.getTotalItems())
                .scrapedItems(task.getScrapedItems())
                .errorItems(task.getErrorItems())
                .errorLog(task.getErrorLog())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private List<MediaItem> filterByMediaTypes(List<MediaItem> items, Set<String> mediaTypes) {
        if (mediaTypes == null || mediaTypes.isEmpty()) {
            return items;
        }
        return items.stream()
                .filter(i -> i.getType() != null && mediaTypes.contains(i.getType()))
                .collect(Collectors.toList());
    }

    /**
     * Parse mediaTypes JSON array string into a set.
     * We keep this lightweight to avoid introducing new DTOs early; schedule APIs will validate inputs.
     */
    private Set<String> parseMediaTypes(String mediaTypesJson) {
        if (mediaTypesJson == null || mediaTypesJson.isBlank()) {
            return Set.of();
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> list = objectMapper.readValue(mediaTypesJson, List.class);
            if (list == null || list.isEmpty()) return Set.of();
            return new HashSet<>(list.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            log.warn("Invalid mediaTypes JSON, ignoring filter: {}", mediaTypesJson, e);
            return Set.of();
        }
    }

    private void broadcastLog(String level, String type, Integer libraryId, String message) {
        SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                .timestamp(System.currentTimeMillis())
                .level(level)
                .source("TASK")
                .type(type)
                .libraryId(libraryId)
                .message(message)
                .build());
    }

}
