package com.mediamanager.ai.service;

import com.mediamanager.ai.dto.AiOrganizationJobStatus;
import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AiOrganizationJobService {

    private static final long DATABASE_YIELD_MILLIS = 25L;

    private final AiOrganizationService organizationService;
    private final AiOrganizationWorker worker;
    private final AiTagTranslationService tagTranslationService;
    private final AiTagSemanticMergeService tagSemanticMergeService;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicReference<AiOrganizationJobStatus> status =
            new AtomicReference<>(AiOrganizationJobStatus.idle());

    public AiOrganizationJobService(
            AiOrganizationService organizationService,
            AiOrganizationWorker worker,
            AiTagTranslationService tagTranslationService,
            AiTagSemanticMergeService tagSemanticMergeService,
            @Qualifier("aiMaintenanceExecutor") Executor executor) {
        this.organizationService = organizationService;
        this.worker = worker;
        this.tagTranslationService = tagTranslationService;
        this.tagSemanticMergeService = tagSemanticMergeService;
        this.executor = executor;
    }

    public AiOrganizationJobStatus getStatus() {
        return status.get();
    }

    public synchronized boolean start(AiOrganizationRequest request) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        AiOrganizationRequest normalized = organizationService.normalize(request);
        cancelRequested.set(false);
        long startedAt = System.currentTimeMillis();
        status.set(AiOrganizationJobStatus.builder()
                .state("queued")
                .phase("queued")
                .libraryId(normalized.getLibraryId())
                .startedAt(startedAt)
                .message("整理任务已进入后台队列")
                .build());

        SecurityContext jobContext = SecurityContextHolder.createEmptyContext();
        jobContext.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        try {
            executor.execute(() -> run(normalized, jobContext, startedAt));
            return true;
        } catch (RuntimeException e) {
            running.set(false);
            status.set(AiOrganizationJobStatus.builder()
                    .state("failed")
                    .phase("queued")
                    .libraryId(normalized.getLibraryId())
                    .startedAt(startedAt)
                    .finishedAt(System.currentTimeMillis())
                    .message("整理任务无法进入后台队列: " + safeMessage(e))
                    .build());
            log.error("Failed to enqueue AI organization job", e);
            return false;
        }
    }

    public boolean cancel() {
        if (!running.get()) {
            return false;
        }
        cancelRequested.set(true);
        status.updateAndGet(current -> current.toBuilder()
                .cancelRequested(true)
                .message("正在等待当前小批次完成后取消")
                .build());
        return true;
    }

    private void run(AiOrganizationRequest request, SecurityContext jobContext, long startedAt) {
        SecurityContextHolder.setContext(jobContext);
        RunStats stats = new RunStats(request.getLibraryId(), startedAt);
        try {
            publish(stats, "running", "preview", "正在生成整理计划", null);
            if (shouldStop(stats)) {
                return;
            }
            AiOrganizationResponse plan = organizationService.preview(request);
            if (shouldStop(stats)) {
                return;
            }

            List<Integer> translationTagIds = worker.listTagIds();
            stats.total += translationTagIds.size();
            publish(stats, "running", "translate", "\u6b63\u5728\u5c06\u6807\u7b7e\u7ffb\u8bd1\u4e3a\u4e2d\u6587", null);
            for (Integer tagId : translationTagIds) {
                if (shouldStop(stats)) {
                    return;
                }
                try {
                    if (worker.translateTag(tagId)) {
                        stats.translatedTagCount++;
                    }
                } catch (Exception e) {
                    stats.failed++;
                    log.warn("Failed to translate tag {} to Chinese: {}", tagId, safeMessage(e));
                }
                stats.processed++;
                publish(stats, "running", "translate", "\u6b63\u5728\u5c06\u6807\u7b7e\u7ffb\u8bd1\u4e3a\u4e2d\u6587", null);
                yieldDatabase();
            }
            if (shouldStop(stats)) {
                return;
            }

            List<AiOrganizationWorker.TagSnapshot> aiTranslationCandidates =
                    tagTranslationService.aiTranslationCandidates(worker.listTagSnapshots());
            stats.total += aiTranslationCandidates.size();
            publish(stats, "running", "ai-translate", "\u6b63\u5728\u7528AI\u7ffb\u8bd1\u5269\u4f59\u6807\u7b7e", null);
            for (List<AiOrganizationWorker.TagSnapshot> batch : tagTranslationService.batches(aiTranslationCandidates)) {
                if (shouldStop(stats)) {
                    return;
                }
                Map<Integer, String> translations;
                try {
                    translations = tagTranslationService.translateBatch(request.getLibraryId(), batch);
                } catch (Exception e) {
                    stats.failed++;
                    translations = Map.of();
                    log.warn("Failed to translate tag batch with AI: {}", safeMessage(e));
                }
                for (AiOrganizationWorker.TagSnapshot candidate : batch) {
                    if (shouldStop(stats)) {
                        return;
                    }
                    try {
                        String translatedName = translations.get(candidate.id());
                        if (translatedName != null && worker.translateTag(candidate.id(), translatedName)) {
                            stats.translatedTagCount++;
                        }
                    } catch (Exception e) {
                        stats.failed++;
                        log.warn("Failed to apply AI tag translation {}: {}",
                                candidate.id(), safeMessage(e));
                    }
                    stats.processed++;
                    publish(stats, "running", "ai-translate", "\u6b63\u5728\u7528AI\u7ffb\u8bd1\u5269\u4f59\u6807\u7b7e", null);
                    yieldDatabase();
                }
            }
            if (shouldStop(stats)) {
                return;
            }
            plan = organizationService.preview(request);

            if (Boolean.TRUE.equals(request.getMergeDuplicateTags())) {
                int mergeTotal = plan.getDuplicateTagGroups().stream()
                        .mapToInt(group -> group.getDuplicateTags().size())
                        .sum();
                stats.total += mergeTotal;
                publish(stats, "running", "merge", "正在合并重复标签", null);
                for (AiOrganizationResponse.DuplicateTagGroup group : plan.getDuplicateTagGroups()) {
                    Integer canonicalId = group.getCanonicalTag() != null ? group.getCanonicalTag().getId() : null;
                    for (AiOrganizationResponse.TagUsage duplicate : group.getDuplicateTags()) {
                        if (shouldStop(stats)) {
                            return;
                        }
                        try {
                            if (worker.mergeTag(canonicalId, duplicate.getId())) {
                                stats.mergedTagCount++;
                            }
                        } catch (Exception e) {
                            stats.failed++;
                            log.warn("Failed to merge tag {} into {}: {}",
                                    duplicate.getId(), canonicalId, safeMessage(e));
                        }
                        stats.processed++;
                        publish(stats, "running", "merge", "正在合并重复标签", null);
                        yieldDatabase();
                    }
                }

                List<AiTagSemanticMergeService.SemanticMergeGroup> semanticGroups =
                        tagSemanticMergeService.suggestGroups(request.getLibraryId(), worker.listTagSnapshots());
                int semanticMergeTotal = semanticGroups.stream()
                        .mapToInt(group -> group.duplicateIds().size())
                        .sum();
                stats.total += semanticMergeTotal;
                publish(stats, "running", "semantic-merge", "\u6b63\u5728\u5408\u5e76AI\u8bc6\u522b\u7684\u540c\u4e49\u6807\u7b7e", null);
                for (AiTagSemanticMergeService.SemanticMergeGroup group : semanticGroups) {
                    for (Integer duplicateId : group.duplicateIds()) {
                        if (shouldStop(stats)) {
                            return;
                        }
                        try {
                            if (worker.mergeTag(group.canonicalId(), duplicateId)) {
                                stats.mergedTagCount++;
                            }
                        } catch (Exception e) {
                            stats.failed++;
                            log.warn("Failed to semantically merge tag {} into {}: {}",
                                    duplicateId, group.canonicalId(), safeMessage(e));
                        }
                        stats.processed++;
                        publish(stats, "running", "semantic-merge", "\u6b63\u5728\u5408\u5e76AI\u8bc6\u522b\u7684\u540c\u4e49\u6807\u7b7e", null);
                        yieldDatabase();
                    }
                }
            }

            if (Boolean.TRUE.equals(request.getDeleteUnusedTags())
                    || Boolean.TRUE.equals(request.getDeleteLowUsageTags())) {
                if (shouldStop(stats)) {
                    return;
                }
                publish(stats, "running", "cleanup-plan", "正在刷新低质量标签清理计划", null);
                List<AiOrganizationResponse.TagUsage> cleanupTags =
                        organizationService.preview(request).getCleanupTags();
                stats.total += cleanupTags.size();
                publish(stats, "running", "cleanup", "正在清理低质量和低引用标签", null);
                for (AiOrganizationResponse.TagUsage tag : cleanupTags) {
                    if (shouldStop(stats)) {
                        return;
                    }
                    try {
                        if (worker.deleteTag(tag.getId())) {
                            stats.deletedCleanupTagCount++;
                            if (tag.getUsageCount() == null || tag.getUsageCount() == 0) {
                                stats.deletedUnusedTagCount++;
                            }
                        }
                    } catch (Exception e) {
                        stats.failed++;
                        log.warn("Failed to delete cleanup tag {}: {}", tag.getId(), safeMessage(e));
                    }
                    stats.processed++;
                    publish(stats, "running", "cleanup", "正在清理低质量和低引用标签", null);
                    yieldDatabase();
                }
            }

            if (Boolean.TRUE.equals(request.getRecolorTags())) {
                if (shouldStop(stats)) {
                    return;
                }
                List<Integer> tagIds = worker.listTagIds();
                stats.total += tagIds.size();
                publish(stats, "running", "recolor", "正在为标签分配易区分的颜色", null);
                for (Integer tagId : tagIds) {
                    if (shouldStop(stats)) {
                        return;
                    }
                    try {
                        if (worker.recolorTag(tagId, Boolean.TRUE.equals(request.getRecolorManualTags()))) {
                            stats.recoloredTagCount++;
                        }
                    } catch (Exception e) {
                        stats.failed++;
                        log.warn("Failed to recolor tag {}: {}", tagId, safeMessage(e));
                    }
                    stats.processed++;
                    publish(stats, "running", "recolor", "正在为标签分配易区分的颜色", null);
                    yieldDatabase();
                }
            }

            List<AiOrganizationResponse.GeneratedCollection> generatedCollections = new ArrayList<>();
            if (Boolean.TRUE.equals(request.getCreateSmartCollections())) {
                if (shouldStop(stats)) {
                    return;
                }
                publish(stats, "running", "collection-plan", "正在刷新智能合集候选", null);
                List<AiOrganizationResponse.SmartCollectionCandidate> candidates =
                        organizationService.preview(request).getSmartCollectionCandidates();
                stats.total += candidates.size();
                publish(stats, "running", "collections", "正在创建智能合集", null);
                for (AiOrganizationResponse.SmartCollectionCandidate candidate : candidates) {
                    if (shouldStop(stats)) {
                        return;
                    }
                    try {
                        AiOrganizationResponse.GeneratedCollection generated =
                                worker.createSmartCollection(request, candidate);
                        generatedCollections.add(generated);
                        if (Boolean.TRUE.equals(generated.getCreated())) {
                            stats.createdCollectionCount++;
                        }
                    } catch (Exception e) {
                        stats.failed++;
                        log.warn("Failed to create smart collection for candidate {}: {}",
                                candidate.getKey(), safeMessage(e));
                    }
                    stats.processed++;
                    publish(stats, "running", "collections", "正在创建智能合集", null);
                    yieldDatabase();
                }
            }

            if (shouldStop(stats)) {
                return;
            }
            publish(stats, "running", "finalize", "正在生成整理结果", null);
            AiOrganizationResponse result = organizationService.previewAfterApply(request);
            result.setMergedTagCount(stats.mergedTagCount);
            result.setTranslatedTagCount(stats.translatedTagCount);
            result.setDeletedCleanupTagCount(stats.deletedCleanupTagCount);
            result.setDeletedUnusedTagCount(stats.deletedUnusedTagCount);
            result.setRecoloredTagCount(stats.recoloredTagCount);
            result.setCreatedCollectionCount(stats.createdCollectionCount);
            result.setGeneratedCollections(List.copyOf(generatedCollections));
            stats.finishedAt = System.currentTimeMillis();
            publish(stats, "done", "done", "标签与合集整理完成", result);
            log.info("AI organization completed processed={} failed={} merged={} translated={} deleted={} recolored={} collections={}",
                    stats.processed,
                    stats.failed,
                    stats.mergedTagCount,
                    stats.translatedTagCount,
                    stats.deletedCleanupTagCount,
                    stats.recoloredTagCount,
                    stats.createdCollectionCount);
        } catch (Exception e) {
            stats.failed++;
            stats.finishedAt = System.currentTimeMillis();
            publish(stats, "failed", status.get().getPhase(), "整理失败: " + safeMessage(e), null);
            log.error("AI organization job failed", e);
        } finally {
            running.set(false);
            SecurityContextHolder.clearContext();
        }
    }

    private boolean shouldStop(RunStats stats) {
        if (!cancelRequested.get()) {
            return false;
        }
        stats.finishedAt = System.currentTimeMillis();
        publish(stats, "cancelled", "cancelled", "整理任务已取消，已完成的修改会保留", null);
        return true;
    }

    private void publish(
            RunStats stats,
            String state,
            String phase,
            String message,
            AiOrganizationResponse result) {
        status.set(AiOrganizationJobStatus.builder()
                .state(state)
                .phase(phase)
                .libraryId(stats.libraryId)
                .total(stats.total)
                .processed(stats.processed)
                .failed(stats.failed)
                .mergedTagCount(stats.mergedTagCount)
                .translatedTagCount(stats.translatedTagCount)
                .deletedCleanupTagCount(stats.deletedCleanupTagCount)
                .deletedUnusedTagCount(stats.deletedUnusedTagCount)
                .recoloredTagCount(stats.recoloredTagCount)
                .createdCollectionCount(stats.createdCollectionCount)
                .cancelRequested(cancelRequested.get())
                .startedAt(stats.startedAt)
                .finishedAt(stats.finishedAt)
                .message(message)
                .result(result)
                .build());
    }

    private void yieldDatabase() {
        try {
            Thread.sleep(DATABASE_YIELD_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelRequested.set(true);
        }
    }

    private String safeMessage(Exception e) {
        return e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : e.getClass().getSimpleName();
    }

    private static final class RunStats {
        private final Integer libraryId;
        private final long startedAt;
        private int total;
        private int processed;
        private int failed;
        private int mergedTagCount;
        private int translatedTagCount;
        private int deletedCleanupTagCount;
        private int deletedUnusedTagCount;
        private int recoloredTagCount;
        private int createdCollectionCount;
        private Long finishedAt;

        private RunStats(Integer libraryId, long startedAt) {
            this.libraryId = libraryId;
            this.startedAt = startedAt;
        }
    }
}
