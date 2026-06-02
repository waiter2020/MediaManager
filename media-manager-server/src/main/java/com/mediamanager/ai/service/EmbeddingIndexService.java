package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.entity.MediaEmbedding;
import com.mediamanager.ai.repository.MediaEmbeddingRepository;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingIndexService {

    private final MediaEmbeddingRepository embeddingRepository;
    private final MediaItemRepository itemRepository;
    private final MovieMetadataRepository movieMetadataRepository;
    private final AiOrchestrator aiOrchestrator;
    private final ObjectMapper objectMapper;

    private String getActiveModelId() {
        Map<String, Object> config = aiOrchestrator.defaultConfig();
        return String.valueOf(config.getOrDefault("embedModel", "nomic-embed-text"));
    }

    @Transactional
    public void indexItem(MediaItem item) {
        String text = buildIndexText(item);
        if (text.isBlank()) {
            return;
        }
        Integer libraryId = item.getLibrary() != null ? item.getLibrary().getId() : null;
        float[] vector = aiOrchestrator.embedText(text, libraryId);
        if (vector.length == 0) {
            log.debug("Skipping embed index for item {}: AI provider returned empty vector", item.getId());
            return;
        }
        MediaEmbedding emb = MediaEmbedding.builder()
                .mediaItemId(item.getId())
                .modelId(getActiveModelId())
                .vector(floatsToBytes(vector))
                .updatedAt(Instant.now())
                .build();
        embeddingRepository.save(emb);
    }

    @Transactional
    public void removeItem(Integer mediaItemId) {
        if (mediaItemId == null) {
            return;
        }
        embeddingRepository.findByMediaItemIdAndModelId(mediaItemId, getActiveModelId())
                .ifPresent(embeddingRepository::delete);
    }

    @Transactional
    public void clearAllEmbeddings() {
        embeddingRepository.deleteAllInBulk();
    }

    public int rebuildAll() {
        clearAllEmbeddings();
        int count = 0;
        int batchCount = 0;
        List<MediaItem> allItems = itemRepository.findAll();
        for (MediaItem item : allItems) {
            if (Boolean.TRUE.equals(item.getHidden())) {
                continue;
            }
            try {
                indexItem(item);
                count++;
                batchCount++;
                if (batchCount >= 10) {
                    batchCount = 0;
                    log.debug("Batch threshold of 10 items reached during reindex. Sleeping for 500ms to throttle AI provider load.");
                    Thread.sleep(500);
                }
            } catch (InterruptedException ie) {
                log.warn("Rebuilding embeddings interrupted!");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Failed to index item {} during rebuild: {}", item.getId(), e.getMessage());
            }
        }
        log.info("Rebuilt embedding index for {} items", count);
        return count;
    }

    public long countEmbeddings() {
        return embeddingRepository.count();
    }

    public boolean hasIndexedVectors() {
        return embeddingRepository.count() > 0;
    }

    @Transactional(readOnly = true)
    public List<Integer> searchSimilar(String query, Set<Integer> libraryIds, int limit) {
        return searchSimilarWithScores(query, libraryIds, limit).stream()
                .map(Scored::itemId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Scored> searchSimilarWithScores(String query, Set<Integer> libraryIds, int limit) {
        float[] queryVec = aiOrchestrator.embedText(query);
        if (queryVec.length == 0) {
            return Collections.emptyList();
        }
        return rankByVector(queryVec, libraryIds, null, limit);
    }

    @Transactional(readOnly = true)
    public List<Scored> searchSimilarToItem(int itemId, Set<Integer> libraryIds, int limit) {
        return embeddingRepository.findByMediaItemIdAndModelId(itemId, getActiveModelId())
                .map(e -> rankByVector(bytesToFloats(e.getVector()), libraryIds, itemId, limit))
                .orElseGet(() -> {
                    MediaItem item = itemRepository.findById(itemId).orElse(null);
                    if (item == null) {
                        return List.of();
                    }
                    Integer libraryId = item.getLibrary() != null ? item.getLibrary().getId() : null;
                    String text = buildIndexText(item);
                    if (text.isBlank()) {
                        return List.of();
                    }
                    float[] queryVec = aiOrchestrator.embedText(text, libraryId);
                    if (queryVec.length == 0) {
                        return List.of();
                    }
                    return rankByVector(queryVec, libraryIds, itemId, limit);
                });
    }

    private List<Scored> rankByVector(float[] queryVec, Set<Integer> libraryIds, Integer excludeItemId, int limit) {
        List<MediaEmbedding> candidates = loadCandidateEmbeddings(libraryIds);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        return candidates.stream()
                .filter(e -> excludeItemId == null || !excludeItemId.equals(e.getMediaItemId()))
                .map(e -> new Scored(e.getMediaItemId(), cosine(queryVec, bytesToFloats(e.getVector()))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<MediaEmbedding> loadCandidateEmbeddings(Set<Integer> libraryIds) {
        String activeModel = getActiveModelId();
        
        // Auto-clear stale index if model changed
        long totalActive = embeddingRepository.countByModelId(activeModel);
        long totalAll = embeddingRepository.count();
        if (totalAll > 0 && totalActive == 0) {
            log.info("Embedding model changed, automatically clearing stale legacy embeddings for model '{}'", activeModel);
            embeddingRepository.deleteAllInBulk();
        }

        if (libraryIds != null && libraryIds.isEmpty()) {
            return List.of();
        }
        if (libraryIds != null) {
            List<Integer> itemIds = itemRepository.findVisibleIdsByLibraryIdsIn(libraryIds);
            if (itemIds.isEmpty()) {
                return List.of();
            }
            return embeddingRepository.findByModelIdAndMediaItemIdIn(activeModel, itemIds);
        }
        return embeddingRepository.findByModelId(activeModel);
    }

    private String buildIndexText(MediaItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getTitle() != null) {
            sb.append(item.getTitle()).append(' ');
        }
        if (item.getOriginalTitle() != null) {
            sb.append(item.getOriginalTitle()).append(' ');
        }

        // Genres from categories
        Set<Category> categories = item.getCategories();
        if (categories != null && !categories.isEmpty()) {
            for (Category cat : categories) {
                if (cat != null && cat.getName() != null) {
                    sb.append(cat.getName()).append(' ');
                }
            }
        }

        // Tags
        Set<Tag> tags = item.getTags();
        if (tags != null && !tags.isEmpty()) {
            for (Tag tag : tags) {
                if (tag != null && tag.getName() != null) {
                    sb.append(tag.getName()).append(' ');
                }
            }
        }

        // MovieMetadata: genres, castInfo, studios
        try {
            MovieMetadata meta = movieMetadataRepository.findByMediaItemId(item.getId()).orElse(null);
            if (meta != null) {
                appendJsonArrayValues(sb, meta.getGenres());
                appendCastNames(sb, meta.getCastInfo());
                appendJsonArrayValues(sb, meta.getStudios());
            }
        } catch (Exception e) {
            log.debug("Failed to load MovieMetadata for item {}: {}", item.getId(), e.getMessage());
        }

        // Overview last so keyword-rich fields get priority in truncated embeddings
        if (item.getOverview() != null) {
            sb.append(item.getOverview());
        }
        return sb.toString().trim();
    }

    /**
     * Parses a JSON array of strings (e.g. genres or studios) and appends each value.
     */
    private void appendJsonArrayValues(StringBuilder sb, String jsonArray) {
        if (jsonArray == null || jsonArray.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            if (node.isArray()) {
                for (JsonNode elem : node) {
                    String val = elem.isTextual() ? elem.asText() : elem.path("name").asText(null);
                    if (val != null && !val.isBlank()) {
                        sb.append(val).append(' ');
                    }
                }
            } else {
                // Fallback: treat as comma-separated string
                for (String part : jsonArray.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        sb.append(trimmed).append(' ');
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: treat as comma-separated string
            for (String part : jsonArray.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    sb.append(trimmed).append(' ');
                }
            }
        }
    }

    /**
     * Parses a JSON array of cast objects and appends actor names.
     * Expected format: [{"name":"Actor Name", "role":"...", ...}, ...]
     */
    private void appendCastNames(StringBuilder sb, String castJson) {
        if (castJson == null || castJson.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(castJson);
            if (node.isArray()) {
                for (JsonNode actor : node) {
                    String name = actor.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        sb.append(name).append(' ');
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse cast JSON: {}", e.getMessage());
        }
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

    public record Scored(int itemId, float score) {}
}
