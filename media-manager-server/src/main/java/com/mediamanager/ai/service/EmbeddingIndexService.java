package com.mediamanager.ai.service;

import com.mediamanager.ai.entity.MediaEmbedding;
import com.mediamanager.ai.repository.MediaEmbeddingRepository;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmbeddingIndexService {

    private static final String DEFAULT_MODEL = "default";

    private final MediaEmbeddingRepository embeddingRepository;
    private final MediaItemRepository itemRepository;
    private final AiOrchestrator aiOrchestrator;

    @Transactional
    public void indexItem(MediaItem item) {
        String text = buildIndexText(item);
        if (text.isBlank()) {
            return;
        }
        float[] vector = aiOrchestrator.embedText(text);
        if (vector.length == 0) {
            return;
        }
        MediaEmbedding emb = MediaEmbedding.builder()
                .mediaItemId(item.getId())
                .modelId(DEFAULT_MODEL)
                .vector(floatsToBytes(vector))
                .updatedAt(Instant.now())
                .build();
        embeddingRepository.save(emb);
    }

    @Transactional(readOnly = true)
    public List<Integer> searchSimilar(String query, Set<Integer> libraryIds, int limit) {
        float[] queryVec = aiOrchestrator.embedText(query);
        if (queryVec.length == 0) {
            return Collections.emptyList();
        }
        List<MediaEmbedding> all = embeddingRepository.findByModelId(DEFAULT_MODEL);
        return all.stream()
                .filter(e -> libraryIds.contains(itemRepository.findById(e.getMediaItemId())
                        .map(i -> i.getLibrary().getId())
                        .orElse(-1)))
                .map(e -> new Scored(e.getMediaItemId(), cosine(queryVec, bytesToFloats(e.getVector()))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .map(Scored::itemId)
                .collect(Collectors.toList());
    }

    private String buildIndexText(MediaItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getTitle() != null) {
            sb.append(item.getTitle()).append(' ');
        }
        if (item.getOverview() != null) {
            sb.append(item.getOverview()).append(' ');
        }
        if (item.getOriginalTitle() != null) {
            sb.append(item.getOriginalTitle());
        }
        return sb.toString().trim();
    }

    private float cosine(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0f;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0f;
        }
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }

    private byte[] floatsToBytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4);
        for (float v : values) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    private float[] bytesToFloats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] values = new float[bytes.length / 4];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getFloat();
        }
        return values;
    }

    private record Scored(int itemId, float score) {}
}
