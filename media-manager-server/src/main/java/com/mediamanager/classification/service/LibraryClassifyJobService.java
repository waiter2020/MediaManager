package com.mediamanager.classification.service;

import com.mediamanager.classification.service.strategy.AiClassifier;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class LibraryClassifyJobService {

    private static final int CLASSIFY_CHUNK_SIZE = 100;
    private static final long DATABASE_YIELD_MILLIS = 150L;

    private final MediaItemRepository itemRepository;
    private final AiClassifier aiClassifier;
    private final LibraryAccessService libraryAccessService;
    private final Executor executor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicInteger lastLibraryId = new AtomicInteger(-1);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicLong startedAt = new AtomicLong(0);
    private final AtomicLong finishedAt = new AtomicLong(0);
    private final AtomicReference<String> state = new AtomicReference<>("idle");
    private final AtomicReference<String> phase = new AtomicReference<>("idle");
    private final AtomicReference<String> message = new AtomicReference<>("暂无 AI 打标任务");

    public LibraryClassifyJobService(
            MediaItemRepository itemRepository,
            AiClassifier aiClassifier,
            LibraryAccessService libraryAccessService,
            @Qualifier("aiMaintenanceExecutor") Executor executor) {
        this.itemRepository = itemRepository;
        this.aiClassifier = aiClassifier;
        this.libraryAccessService = libraryAccessService;
        this.executor = executor;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("running", running.get());
        snapshot.put("state", state.get());
        snapshot.put("phase", phase.get());
        snapshot.put("libraryId", lastLibraryId.get() > 0 ? lastLibraryId.get() : null);
        snapshot.put("total", total.get());
        snapshot.put("processed", processed.get());
        snapshot.put("failed", failed.get());
        snapshot.put("cancelRequested", cancelRequested.get());
        snapshot.put("startedAt", startedAt.get() > 0 ? startedAt.get() : null);
        snapshot.put("finishedAt", finishedAt.get() > 0 ? finishedAt.get() : null);
        snapshot.put("message", message.get());
        return Collections.unmodifiableMap(snapshot);
    }

    public synchronized boolean startLibraryClassify(Integer libraryId) {
        libraryAccessService.assertCanEditLibrary(libraryId);
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        lastLibraryId.set(libraryId);
        total.set(0);
        processed.set(0);
        failed.set(0);
        cancelRequested.set(false);
        startedAt.set(System.currentTimeMillis());
        finishedAt.set(0);
        state.set("queued");
        phase.set("queued");
        message.set("AI 打标任务已进入后台队列");
        try {
            executor.execute(() -> runClassify(libraryId));
            return true;
        } catch (RuntimeException e) {
            running.set(false);
            state.set("failed");
            phase.set("queued");
            finishedAt.set(System.currentTimeMillis());
            message.set("AI 打标任务无法进入后台队列: " + safeMessage(e));
            log.error("Failed to enqueue library AI classification for library {}", libraryId, e);
            return false;
        }
    }

    public boolean cancel() {
        if (!running.get()) {
            return false;
        }
        cancelRequested.set(true);
        message.set("正在等待当前 AI 请求完成后取消");
        return true;
    }

    private void runClassify(Integer libraryId) {
        try {
            state.set("running");
            phase.set("counting");
            message.set("正在统计待打标媒体");
            total.set((int) Math.min(Integer.MAX_VALUE, itemRepository.countVisibleVideosByLibraryId(libraryId)));

            int afterId = 0;
            while (!cancelRequested.get()) {
                phase.set("loading");
                message.set("正在读取下一批媒体");
                List<MediaItem> chunk = itemRepository.findVisibleVideosForAiClassification(
                        libraryId,
                        afterId,
                        PageRequest.of(0, CLASSIFY_CHUNK_SIZE));
                if (chunk.isEmpty()) {
                    break;
                }
                afterId = chunk.getLast().getId();

                phase.set("ai-tagging");
                message.set("AI 正在优先匹配已有标签");
                try {
                    aiClassifier.classifyBatch(List.copyOf(chunk));
                } catch (Exception e) {
                    failed.addAndGet(chunk.size());
                    log.warn("AI tagging chunk failed for library {} after item {}: {}",
                            libraryId, afterId, safeMessage(e));
                } finally {
                    processed.addAndGet(chunk.size());
                }
                yieldDatabase();
            }

            finishedAt.set(System.currentTimeMillis());
            if (cancelRequested.get()) {
                state.set("cancelled");
                phase.set("cancelled");
                message.set("AI 打标任务已取消，已生成的建议会保留");
            } else {
                state.set("done");
                phase.set("done");
                message.set("AI 打标完成，请审核生成的标签建议");
            }
            log.info("Library AI tagging finished libraryId={} state={} processed={} failed={}",
                    libraryId, state.get(), processed.get(), failed.get());
        } catch (Exception e) {
            failed.incrementAndGet();
            finishedAt.set(System.currentTimeMillis());
            state.set("failed");
            message.set("AI 打标失败: " + safeMessage(e));
            log.error("Library AI tagging failed for library {}", libraryId, e);
        } finally {
            running.set(false);
        }
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
}
