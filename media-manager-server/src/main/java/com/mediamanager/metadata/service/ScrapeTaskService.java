package com.mediamanager.metadata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.MediaPostProcessService;
import com.mediamanager.metadata.dto.ScrapeTaskCreateRequest;
import com.mediamanager.metadata.dto.ScrapeTaskPreviewResponse;
import com.mediamanager.metadata.dto.ScrapeTaskResponse;
import com.mediamanager.metadata.entity.ScrapeTask;
import com.mediamanager.metadata.repository.ScrapeTaskRepository;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.util.FileNameParser;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import com.mediamanager.sync.service.SseService;
import com.mediamanager.system.dto.SystemLogEventDto;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SystemLogBroadcaster;
import com.mediamanager.common.security.SecurityCurrentUser;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
    private final LibraryAccessService libraryAccessService;
    private final SecurityCurrentUser securityCurrentUser;
    private final TransactionTemplate transactionTemplate;
    private final LibraryPluginConfigRepository pluginConfigRepository;

    @Value("${mediamanager.scraper.request-delay-ms:2000}")
    private long requestDelayMs;

    @Value("${mediamanager.scraper.batch-size:50}")
    private int batchSize;

    /** Track cancelled task IDs so async thread can check */
    private final Map<Integer, Boolean> cancelledTasks = new ConcurrentHashMap<>();

    /** Basic library-level mutex to avoid hammering same library concurrently (single-instance). */
    private final Map<Integer, Object> libraryLocks = new ConcurrentHashMap<>();
    private final ExecutorService scrapeExecutor = Executors.newFixedThreadPool(2);

    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCESS", "FAILED", "CANCELLED");
    private static final Set<String> VALID_MEDIA_TYPES = Set.of("MOVIE", "TV_SHOW", "EPISODE", "IMAGE", "AUDIO");

    @PreDestroy
    public void shutdown() {
        scrapeExecutor.shutdown();
        try {
            if (!scrapeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scrapeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scrapeExecutor.shutdownNow();
        }
    }

    private void transitionStatus(ScrapeTask task, String newStatus) {
        String current = task.getStatus();
        if (TERMINAL_STATUSES.contains(current)) {
            log.warn("Ignoring illegal scrape task transition {} -> {} for task {}", current, newStatus, task.getId());
            return;
        }
        if ("PENDING".equals(current) && !Set.of("RUNNING", "CANCELLED").contains(newStatus)) {
            log.warn("Ignoring illegal scrape task transition {} -> {} for task {}", current, newStatus, task.getId());
            return;
        }
        if ("RUNNING".equals(current) && !Set.of("SUCCESS", "FAILED", "CANCELLED").contains(newStatus)) {
            log.warn("Ignoring illegal scrape task transition {} -> {} for task {}", current, newStatus, task.getId());
            return;
        }
        task.setStatus(newStatus);
    }

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

    public ScrapeTaskResponse startManualScrape(ScrapeTaskCreateRequest request) {
        ScrapeTaskCreateRequest safe = request != null ? request : new ScrapeTaskCreateRequest();
        String mediaTypesJson = buildMediaTypesJson(safe.getMediaTypes());
        String paramsJson = buildManualParamsJson(safe.getRequestDelayMs(), safe.getBatchSize());
        return startScrapeWithParams(
                null,
                safe.getLibraryId(),
                "MANUAL",
                safe.getTargetStatus() != null ? safe.getTargetStatus() : "UNIDENTIFIED",
                mediaTypesJson,
                paramsJson);
    }

    @Transactional(readOnly = true)
    public ScrapeTaskPreviewResponse previewManualScrape(ScrapeTaskCreateRequest request) {
        ScrapeTaskCreateRequest safe = request != null ? request : new ScrapeTaskCreateRequest();
        Integer libraryId = safe.getLibraryId();
        String targetStatus = safe.getTargetStatus() != null ? safe.getTargetStatus() : "UNIDENTIFIED";
        String mediaTypesJson = buildMediaTypesJson(safe.getMediaTypes());
        Set<String> mediaTypes = parseMediaTypes(mediaTypesJson);

        List<MediaItem> scopeItems = findVisibleItemsByScope(libraryId);
        List<MediaItem> statusItems = filterByTargetStatus(scopeItems, targetStatus);
        List<MediaItem> eligibleItems = filterByMediaTypes(statusItems, mediaTypes);

        MediaLibrary library = libraryId != null
                ? libraryRepository.findById(libraryId).orElse(null)
                : null;
        List<String> enabledScrapers = enabledScrapers(libraryId);
        List<String> tips = buildPreviewTips(targetStatus, mediaTypes, scopeItems, statusItems, eligibleItems, enabledScrapers);

        return ScrapeTaskPreviewResponse.builder()
                .libraryId(libraryId)
                .libraryName(library != null ? library.getName() : null)
                .targetStatus(targetStatus)
                .mediaTypes(mediaTypes.stream().toList())
                .totalItems(eligibleItems.size())
                .allVisibleItems(scopeItems.size())
                .byStatus(countBy(scopeItems, MediaItem::getStatus))
                .byType(countBy(statusItems, MediaItem::getType))
                .enabledScrapers(enabledScrapers)
                .tips(tips)
                .build();
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

        Set<Integer> viewableLibraries = libraryAccessService.resolveLibraryFilter(libraryId);
        if (viewableLibraries.isEmpty()) {
            log.warn("No viewable libraries for scrape (libraryId={})", libraryId);
            return null;
        }

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

        scrapeExecutor.submit(() -> executeScrapeInBackground(task.getId()));

        return toResponse(task);
    }

    @Transactional
    public boolean cancelTask(Integer taskId) {
        return scrapeTaskRepository.findById(taskId).map(task -> {
            assertCanViewTask(task);
            if ("RUNNING".equals(task.getStatus()) || "PENDING".equals(task.getStatus())) {
                cancelledTasks.put(taskId, true);
                transitionStatus(task, "CANCELLED");
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
                .filter(this::canViewTask)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScrapeTaskResponse> getTasksByScheduleId(Long scheduleId) {
        return scrapeTaskRepository.findByScheduleIdOrderByCreatedAtDesc(scheduleId).stream()
                .filter(this::canViewTask)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScrapeTaskResponse getTask(Integer taskId) {
        return scrapeTaskRepository.findById(taskId)
                .filter(this::canViewTask)
                .map(this::toResponse)
                .orElse(null);
    }

    // ── Async Execution ─────────────────────────────────────

    public void executeScrapeInBackground(Integer taskId) {
        TaskSnapshot snapshot = markTaskRunning(taskId);
        if (snapshot == null) {
            return;
        }

        sseService.broadcast("scrape-start", "Scrape task " + taskId + " started");
        broadcastLog("INFO", "SCRAPE_START", snapshot.libraryId(), "Started scrape task " + taskId);

        List<MediaItem> items = findItemsByTarget(snapshot.libraryId(), snapshot.targetStatus(), snapshot.mediaTypesJson());
        if (snapshot.batchSize() > 0 && items.size() > snapshot.batchSize()) {
            items = new ArrayList<>(items.subList(0, snapshot.batchSize()));
        }

        int scraped = 0;
        int errors = 0;
        StringBuilder errorLog = new StringBuilder();

        Object lock = snapshot.libraryId() != null
                ? libraryLocks.computeIfAbsent(snapshot.libraryId(), k -> new Object())
                : new Object();
        try {
            synchronized (lock) {
                for (MediaItem item : items) {
                    if (cancelledTasks.remove(taskId) != null || isTaskCancelled(taskId)) {
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

                    updateTaskProgress(taskId, scraped, errors, errorLog.toString());

                    if (snapshot.requestDelayMs() > 0) {
                        try {
                            Thread.sleep(snapshot.requestDelayMs());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } finally {
            if (snapshot.libraryId() != null) {
                libraryLocks.remove(snapshot.libraryId(), lock);
            }
        }

        finalizeTask(taskId, scraped, errors, errorLog.toString());
    }

    private TaskSnapshot markTaskRunning(Integer taskId) {
        return transactionTemplate.execute(status -> scrapeTaskRepository.findById(taskId)
                .map(task -> {
                    if ("CANCELLED".equals(task.getStatus())) {
                        return null;
                    }
                    transitionStatus(task, "RUNNING");
                    task.setStartedAt(Instant.now());
                    scrapeTaskRepository.save(task);
                    Integer libraryId = task.getLibrary() != null ? task.getLibrary().getId() : null;
                    String effectiveTarget = task.getTargetStatus() != null ? task.getTargetStatus() : "UNIDENTIFIED";
                    String effectiveMediaTypesJson = (task.getMediaTypes() != null && !task.getMediaTypes().isBlank())
                            ? task.getMediaTypes()
                            : null;
                    ScrapeParams params = parseParams(task.getParamsJson());
                    return new TaskSnapshot(
                            libraryId,
                            effectiveTarget,
                            effectiveMediaTypesJson,
                            params.requestDelayMs(),
                            params.batchSize());
                })
                .orElse(null));
    }

    // ── Item-level scraping ─────────────────────────────────

    private void scrapeItem(MediaItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        List<MediaFile> files = mediaFileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);

        MetadataResult pipelineResult = pipelineService.executeScrapePipeline(item, primaryFile);
        if (pipelineResult.getTitle() == null && primaryFile != null) {
            MetadataResult fallback = fileNameParser.parse(primaryFile.getFileName());
            pipelineResult.mergeFrom(fallback);
        }
        metadataApplyService.applyResult(item.getId(), pipelineResult, primaryFile != null ? primaryFile.getId() : null);
        mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
    }

    private boolean isTaskCancelled(Integer taskId) {
        return transactionTemplate.execute(status -> scrapeTaskRepository.findById(taskId)
                .map(task -> "CANCELLED".equals(task.getStatus()))
                .orElse(true));
    }

    // ── Progress helpers ────────────────────────────────────

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTaskProgress(Integer taskId, int scraped, int errors, String errorLog) {
        scrapeTaskRepository.findById(taskId).ifPresent(task -> {
            task.setScrapedItems(scraped);
            task.setErrorItems(errors);
            if (errorLog != null && !errorLog.isEmpty()) task.setErrorLog(errorLog);
            scrapeTaskRepository.save(task);

            Map<String, Object> progressPayload = Map.of(
                    "taskId", taskId,
                    "status", "RUNNING",
                    "scraped", scraped,
                    "errors", errors,
                    "total", task.getTotalItems()
            );
            sseService.broadcastBoth("scrape-progress", "scrape.task.updated", progressPayload);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeTask(Integer taskId, int scraped, int errors, String errorLog) {
        scrapeTaskRepository.findById(taskId).ifPresent(task -> {
            if (!"CANCELLED".equals(task.getStatus())) {
                transitionStatus(task, errors > 0 && scraped == 0 ? "FAILED" : "SUCCESS");
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
        return filterByMediaTypes(filterByTargetStatus(findVisibleItemsByScope(libraryId), targetStatus), mediaTypes);
    }

    private List<MediaItem> findVisibleItemsByScope(Integer libraryId) {
        Set<Integer> viewableLibraries = libraryAccessService.resolveLibraryFilter(libraryId);
        if (viewableLibraries.isEmpty()) {
            return List.of();
        }
        List<MediaItem> items = (libraryId != null)
                ? mediaItemRepository.findByLibraryIdOrderByIdAsc(libraryId)
                : mediaItemRepository.findAllByOrderByIdAsc();
        return items.stream()
                .filter(this::isVisible)
                .filter(i -> i.getLibrary() != null && viewableLibraries.contains(i.getLibrary().getId()))
                .collect(Collectors.toList());
    }

    private List<MediaItem> filterByTargetStatus(List<MediaItem> items, String targetStatus) {
        if ("ALL".equalsIgnoreCase(targetStatus)) {
            return items;
        }
        String effective = targetStatus != null ? targetStatus : "UNIDENTIFIED";
        return items.stream()
                .filter(item -> effective.equalsIgnoreCase(item.getStatus()))
                .collect(Collectors.toList());
    }

    private boolean isVisible(MediaItem item) {
        return item != null && !Boolean.TRUE.equals(item.getHidden());
    }

    private boolean canViewTask(ScrapeTask task) {
        if (task.getLibrary() == null) {
            return libraryAccessService.bypassesLibraryRestrictions(
                    securityCurrentUser.getCurrentUser().orElse(null));
        }
        return libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser())
                .contains(task.getLibrary().getId());
    }

    private void assertCanViewTask(ScrapeTask task) {
        if (!canViewTask(task)) {
            throw new com.mediamanager.common.exception.BusinessException(
                    com.mediamanager.common.exception.ErrorCode.LIBRARY_ACCESS_DENIED);
        }
    }

    private ScrapeTaskResponse toResponse(ScrapeTask task) {
        ScrapeParams params = parseParams(task.getParamsJson());
        int total = task.getTotalItems() != null ? task.getTotalItems() : 0;
        int scraped = task.getScrapedItems() != null ? task.getScrapedItems() : 0;
        int errors = task.getErrorItems() != null ? task.getErrorItems() : 0;
        int processed = Math.min(total, scraped + errors);
        int progressPercent = total > 0 ? (int) Math.round((processed * 100.0) / total) : 0;
        return ScrapeTaskResponse.builder()
                .id(task.getId())
                .scheduleId(task.getScheduleId())
                .libraryId(task.getLibrary() != null ? task.getLibrary().getId() : null)
                .libraryName(task.getLibrary() != null ? task.getLibrary().getName() : null)
                .status(task.getStatus())
                .triggerType(task.getTriggerType())
                .targetStatus(task.getTargetStatus())
                .mediaTypes(task.getMediaTypes())
                .paramsJson(task.getParamsJson())
                .requestDelayMs(params.requestDelayMs())
                .batchSize(params.batchSize())
                .progressPercent(progressPercent)
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

    private Map<String, Integer> countBy(List<MediaItem> items, Function<MediaItem, String> classifier) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (MediaItem item : items) {
            String key = classifier.apply(item);
            if (key == null || key.isBlank()) {
                key = "UNKNOWN";
            }
            result.merge(key, 1, Integer::sum);
        }
        return result;
    }

    private List<String> enabledScrapers(Integer libraryId) {
        Set<Integer> viewableLibraries = libraryAccessService.resolveLibraryFilter(libraryId);
        if (viewableLibraries.isEmpty()) {
            return List.of();
        }
        List<Integer> libraryIds = libraryId != null ? List.of(libraryId) : viewableLibraries.stream().toList();
        return libraryIds.stream()
                .flatMap(id -> pluginConfigRepository.findByLibrary_IdOrderByPriorityAsc(id).stream())
                .filter(config -> PluginKind.SCRAPER.name().equalsIgnoreCase(config.getKind()))
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .map(config -> config.getPluginId() != null ? config.getPluginId().trim().toLowerCase() : "")
                .filter(pluginId -> !pluginId.isBlank())
                .distinct()
                .toList();
    }

    private List<String> buildPreviewTips(
            String targetStatus,
            Set<String> mediaTypes,
            List<MediaItem> scopeItems,
            List<MediaItem> statusItems,
            List<MediaItem> eligibleItems,
            List<String> enabledScrapers) {
        List<String> tips = new ArrayList<>();
        if (scopeItems.isEmpty()) {
            tips.add("当前范围没有可见媒体，请先扫描媒体库。");
            return tips;
        }
        if (statusItems.isEmpty() && !"ALL".equalsIgnoreCase(targetStatus)) {
            tips.add("当前范围没有 " + targetStatus + " 状态的媒体，可将目标状态改为 ALL。");
        }
        if (!statusItems.isEmpty() && eligibleItems.isEmpty() && mediaTypes != null && !mediaTypes.isEmpty()) {
            tips.add("目标状态下没有选中媒体类型，可清空媒体类型筛选或选择已有类型。");
        }
        if (enabledScrapers == null || enabledScrapers.isEmpty()) {
            tips.add("当前范围未发现启用的 SCRAPER，任务将无法拉取远程元数据。");
        }
        return tips;
    }

    private String buildMediaTypesJson(List<String> mediaTypes) {
        if (mediaTypes == null || mediaTypes.isEmpty()) {
            return null;
        }
        List<String> normalized = mediaTypes.stream()
                .filter(type -> type != null && !type.isBlank())
                .map(type -> type.trim().toUpperCase())
                .distinct()
                .toList();
        for (String type : normalized) {
            if (!VALID_MEDIA_TYPES.contains(type)) {
                throw new com.mediamanager.common.exception.BusinessException(
                        com.mediamanager.common.exception.ErrorCode.VALIDATION_ERROR,
                        "Unsupported media type: " + type);
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new com.mediamanager.common.exception.BusinessException(
                    com.mediamanager.common.exception.ErrorCode.VALIDATION_ERROR,
                    "Failed to serialize mediaTypes");
        }
    }

    private String buildManualParamsJson(Long requestDelayMs, Integer batchSize) {
        try {
            Map<String, Object> params = new java.util.LinkedHashMap<>();
            if (requestDelayMs != null) {
                params.put("requestDelayMs", Math.max(0L, requestDelayMs));
            }
            if (batchSize != null) {
                params.put("batchSize", Math.max(1, batchSize));
            }
            return params.isEmpty() ? null : objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new com.mediamanager.common.exception.BusinessException(
                    com.mediamanager.common.exception.ErrorCode.VALIDATION_ERROR,
                    "Failed to serialize scrape params");
        }
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
                    .map(s -> s.trim().toUpperCase())
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            log.warn("Invalid mediaTypes JSON, ignoring filter: {}", mediaTypesJson, e);
            return Set.of();
        }
    }

    private ScrapeParams parseParams(String paramsJson) {
        long effectiveDelay = requestDelayMs;
        int effectiveBatchSize = batchSize;
        if (paramsJson == null || paramsJson.isBlank()) {
            return new ScrapeParams(effectiveDelay, effectiveBatchSize);
        }
        try {
            Map<?, ?> map = objectMapper.readValue(paramsJson, Map.class);
            Object delay = map.get("requestDelayMs");
            Object size = map.get("batchSize");
            if (delay instanceof Number n) {
                effectiveDelay = Math.max(0, n.longValue());
            }
            if (size instanceof Number n) {
                effectiveBatchSize = Math.max(1, n.intValue());
            }
        } catch (Exception e) {
            log.warn("Invalid scrape params JSON, using defaults: {}", paramsJson, e);
        }
        return new ScrapeParams(effectiveDelay, effectiveBatchSize);
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

    private record TaskSnapshot(
            Integer libraryId,
            String targetStatus,
            String mediaTypesJson,
            long requestDelayMs,
            int batchSize) {}

    private record ScrapeParams(long requestDelayMs, int batchSize) {}

}
