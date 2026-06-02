package com.mediamanager.classification.service;

import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryClassifyJobService {

    private final MediaItemRepository itemRepository;
    private final ClassificationEngine classificationEngine;
    private final LibraryAccessService libraryAccessService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger lastLibraryId = new AtomicInteger(-1);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);

    public Map<String, Object> getStatus() {
        return Map.of(
                "running", running.get(),
                "libraryId", lastLibraryId.get() > 0 ? lastLibraryId.get() : null,
                "processed", processed.get(),
                "failed", failed.get());
    }

    public boolean startLibraryClassify(Integer libraryId) {
        libraryAccessService.assertCanEditLibrary(libraryId);
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        lastLibraryId.set(libraryId);
        processed.set(0);
        failed.set(0);
        CompletableFuture.runAsync(() -> runClassify(libraryId));
        return true;
    }

    private void runClassify(Integer libraryId) {
        try {
            List<MediaItem> items = itemRepository.findByLibraryIdOrderByIdAsc(libraryId);
            for (MediaItem item : items) {
                if (Boolean.TRUE.equals(item.getHidden())) {
                    continue;
                }
                try {
                    classificationEngine.executeClassification(item);
                    processed.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.warn("Classify failed for item {}: {}", item.getId(), e.getMessage());
                }
            }
            log.info("Library classify finished libraryId={} processed={} failed={}",
                    libraryId, processed.get(), failed.get());
        } finally {
            running.set(false);
        }
    }
}
