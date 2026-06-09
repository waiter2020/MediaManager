package com.mediamanager.classification.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.spi.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagEmbeddingCacheService {

    private static final int BATCH_SIZE = 32;
    private static final int MAX_CONCURRENT_REQUESTS = 2;
    private static final long BATCH_PAUSE_MS = 300L;
    private static final long SEMAPHORE_TIMEOUT_MS = 120_000L;

    private final AiOrchestrator aiOrchestrator;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final Cache<String, float[]> cache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();
    private final Semaphore requestSemaphore = new Semaphore(MAX_CONCURRENT_REQUESTS);

    public boolean isEmbeddingAvailable(Integer libraryId) {
        AiProvider provider = aiOrchestrator.resolve(libraryId, AiTaskType.EMBED_TEXT);
        return provider != null && !"noop".equalsIgnoreCase(provider.providerId());
    }

    public float[] embedOne(String text, Integer libraryId) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String cacheKey = cacheKey(text, libraryId);
        float[] cached = cache.getIfPresent(cacheKey);
        if (cached != null && cached.length > 0) {
            return cached;
        }
        Map<String, float[]> embedded = embedAll(List.of(text), libraryId);
        return embedded.getOrDefault(normalizedText(text), new float[0]);
    }

    public Map<String, float[]> embedAll(List<String> texts, Integer libraryId) {
        Map<String, float[]> results = new LinkedHashMap<>();
        if (texts == null || texts.isEmpty() || !isEmbeddingAvailable(libraryId)) {
            return results;
        }

        Set<String> normalizedTexts = new LinkedHashSet<>();
        for (String text : texts) {
            String normalized = normalizedText(text);
            if (!normalized.isBlank()) {
                normalizedTexts.add(normalized);
            }
        }
        if (normalizedTexts.isEmpty()) {
            return results;
        }

        List<String> missing = new ArrayList<>();
        for (String normalized : normalizedTexts) {
            float[] cached = cache.getIfPresent(cacheKey(normalized, libraryId));
            if (cached != null && cached.length > 0) {
                results.put(normalized, cached);
            } else {
                missing.add(normalized);
            }
        }

        for (int i = 0; i < missing.size(); i += BATCH_SIZE) {
            List<String> batch = missing.subList(i, Math.min(i + BATCH_SIZE, missing.size()));
            Map<String, float[]> embeddedBatch = embedBatch(batch, libraryId);
            results.putAll(embeddedBatch);
            if (i + BATCH_SIZE < missing.size()) {
                sleepQuietly(BATCH_PAUSE_MS);
            }
        }
        return results;
    }

    private Map<String, float[]> embedBatch(List<String> batch, Integer libraryId) {
        Map<String, float[]> embedded = new LinkedHashMap<>();
        if (batch.isEmpty()) {
            return embedded;
        }
        boolean acquired = false;
        try {
            acquired = requestSemaphore.tryAcquire(SEMAPHORE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                log.warn("Timed out waiting for tag embedding permit; skipping batch of {}", batch.size());
                return embedded;
            }
            List<float[]> vectors = aiOrchestrator.embedTexts(batch, libraryId);
            for (int index = 0; index < batch.size(); index++) {
                String normalized = batch.get(index);
                float[] vector = index < vectors.size() ? vectors.get(index) : new float[0];
                if (vector.length > 0) {
                    cache.put(cacheKey(normalized, libraryId), vector);
                    embedded.put(normalized, vector);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for tag embedding permit");
        } catch (Exception e) {
            log.warn("Failed to embed tag batch: {}", e.getMessage());
        } finally {
            if (acquired) {
                requestSemaphore.release();
            }
        }
        return embedded;
    }

    private String normalizedText(String text) {
        return tagCanonicalizationService.normalizeDisplayName(text);
    }

    private String cacheKey(String text, Integer libraryId) {
        return aiOrchestrator.embedModelId(libraryId) + ":" + normalizedText(text);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
