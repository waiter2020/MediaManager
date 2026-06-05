package com.mediamanager.search.service;

import com.mediamanager.ai.service.EmbeddingIndexService;
import com.mediamanager.search.dto.SearchReindexStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchReindexJobService {

    private final FtsIndexService ftsIndexService;
    private final EmbeddingIndexService embeddingIndexService;

    private final AtomicReference<SearchReindexStatus> status = new AtomicReference<>(SearchReindexStatus.idle());

    public SearchReindexStatus getStatus() {
        return status.get();
    }

    public boolean isRunning() {
        SearchReindexStatus current = status.get();
        return current != null && "running".equals(current.getState());
    }

    public synchronized boolean startRebuildAll() {
        if (isRunning()) {
            return false;
        }
        long now = System.currentTimeMillis();
        status.set(SearchReindexStatus.builder()
                .state("running")
                .phase("fts")
                .ftsIndexed(0)
                .embedIndexed(0)
                .message("正在重建全文索引…")
                .startedAt(now)
                .updatedAt(now)
                .build());
        CompletableFuture.runAsync(this::runRebuildAll);
        return true;
    }

    private void runRebuildAll() {
        long startedAt = status.get().getStartedAt();
        try {
            int fts = ftsIndexService.rebuildAll();
            long t1 = System.currentTimeMillis();
            status.set(SearchReindexStatus.builder()
                    .state("running")
                    .phase("embed")
                    .ftsIndexed(fts)
                    .embedIndexed(0)
                    .message("正在重建向量索引（依赖 AI 嵌入服务，可能较慢）…")
                    .startedAt(startedAt)
                    .updatedAt(t1)
                    .build());

            int embed = embeddingIndexService.rebuildAll();
            long doneAt = System.currentTimeMillis();
            status.set(SearchReindexStatus.builder()
                    .state("done")
                    .phase("done")
                    .ftsIndexed(fts)
                    .embedIndexed(embed)
                    .message(String.format("索引重建完成：FTS %d 条，向量 %d 条", fts, embed))
                    .startedAt(startedAt)
                    .updatedAt(doneAt)
                    .build());
            log.info("Search reindex completed: fts={}, embed={}", fts, embed);
        } catch (Exception e) {
            log.error("Search reindex failed", e);
            long failedAt = System.currentTimeMillis();
            SearchReindexStatus prev = status.get();
            status.set(SearchReindexStatus.builder()
                    .state("failed")
                    .phase(prev != null ? prev.getPhase() : "unknown")
                    .ftsIndexed(prev != null ? prev.getFtsIndexed() : 0)
                    .embedIndexed(prev != null ? prev.getEmbedIndexed() : 0)
                    .message("索引重建失败: " + e.getMessage())
                    .startedAt(startedAt)
                    .updatedAt(failedAt)
                    .build());
        }
    }
}
