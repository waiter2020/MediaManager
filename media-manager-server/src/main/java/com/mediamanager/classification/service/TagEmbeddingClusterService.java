package com.mediamanager.classification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagEmbeddingClusterService {

    private static final double CLUSTER_THRESHOLD = 0.88;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.95;
    private static final double NEIGHBOR_THRESHOLD = 0.92;

    private final TagEmbeddingCacheService tagEmbeddingCacheService;
    private final TagSimilarityService tagSimilarityService;
    private final TagCanonicalizationService tagCanonicalizationService;

    public record EmbeddingCluster(
            Integer canonicalId,
            List<Integer> memberIds,
            double confidence,
            String reason) {
    }

    public record NeighborMatch(Integer tagId, String tagName, double score) {
    }

    public boolean isEmbeddingAvailable(Integer libraryId) {
        return tagEmbeddingCacheService.isEmbeddingAvailable(libraryId);
    }

    public List<EmbeddingCluster> clusterByEmbedding(
            List<TagMergeSnapshot> tags,
            Integer libraryId) {
        if (tags == null || tags.size() < 2 || !isEmbeddingAvailable(libraryId)) {
            return List.of();
        }
        Map<Integer, float[]> vectors = embedTags(tags, libraryId);
        if (vectors.size() < 2) {
            return List.of();
        }
        UnionFind unionFind = new UnionFind(vectors.keySet());
        Map<String, Double> pairScores = new HashMap<>();

        List<Integer> ids = new ArrayList<>(vectors.keySet());
        for (int i = 0; i < ids.size(); i++) {
            Integer leftId = ids.get(i);
            float[] leftVec = vectors.get(leftId);
            TagMergeSnapshot left = findTag(tags, leftId);
            for (int j = i + 1; j < ids.size(); j++) {
                Integer rightId = ids.get(j);
                TagMergeSnapshot right = findTag(tags, rightId);
                if (left == null || right == null) {
                    continue;
                }
                if (tagSimilarityService.shouldBlockMerge(left.name(), right.name())) {
                    continue;
                }
                if (singlePrefixOnly(left.name(), right.name())) {
                    continue;
                }
                double score = cosine(leftVec, vectors.get(rightId));
                if (score >= CLUSTER_THRESHOLD) {
                    unionFind.union(leftId, rightId);
                    pairScores.put(pairKey(leftId, rightId), score);
                }
            }
        }

        List<EmbeddingCluster> clusters = new ArrayList<>();
        for (Set<Integer> group : unionFind.groups()) {
            if (group.size() < 2) {
                continue;
            }
            List<TagMergeSnapshot> members = group.stream()
                    .map(id -> findTag(tags, id))
                    .filter(tag -> tag != null)
                    .sorted((left, right) -> Long.compare(
                            right.usageCount() != null ? right.usageCount() : 0L,
                            left.usageCount() != null ? left.usageCount() : 0L))
                    .toList();
            if (members.size() < 2) {
                continue;
            }
            double confidence = groupConfidence(group, pairScores);
            clusters.add(new EmbeddingCluster(
                    members.getFirst().id(),
                    members.stream().skip(1).map(TagMergeSnapshot::id).toList(),
                    confidence,
                    "embedding"));
        }
        return clusters;
    }

    public Optional<NeighborMatch> findNearestNeighbor(
            String rawName,
            List<TagMergeSnapshot> candidates,
            Integer libraryId) {
        if (!isEmbeddingAvailable(libraryId) || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String name = tagCanonicalizationService.normalizeDisplayName(rawName);
        if (name.isBlank()) {
            return Optional.empty();
        }
        float[] query = tagEmbeddingCacheService.embedOne(name, libraryId);
        if (query.length == 0) {
            return Optional.empty();
        }

        List<String> candidateNames = candidates.stream()
                .filter(candidate -> candidate != null && candidate.name() != null && !candidate.name().isBlank())
                .filter(candidate -> !tagSimilarityService.shouldBlockMerge(name, candidate.name()))
                .map(TagMergeSnapshot::name)
                .toList();
        Map<String, float[]> vectors = tagEmbeddingCacheService.embedAll(candidateNames, libraryId);

        NeighborMatch best = null;
        for (TagMergeSnapshot candidate : candidates) {
            if (candidate == null || candidate.id() == null || candidate.name() == null) {
                continue;
            }
            if (tagSimilarityService.shouldBlockMerge(name, candidate.name())) {
                continue;
            }
            String normalizedCandidate = tagCanonicalizationService.normalizeDisplayName(candidate.name());
            float[] vector = vectors.get(normalizedCandidate);
            if (vector == null || vector.length == 0) {
                continue;
            }
            double score = cosine(query, vector);
            if (score >= NEIGHBOR_THRESHOLD && (best == null || score > best.score())) {
                best = new NeighborMatch(candidate.id(), candidate.name(), score);
            }
        }
        return Optional.ofNullable(best);
    }

    private Map<Integer, float[]> embedTags(List<TagMergeSnapshot> tags, Integer libraryId) {
        Map<Integer, float[]> vectors = new LinkedHashMap<>();
        List<String> names = tags.stream()
                .filter(tag -> tag != null && tag.id() != null && tag.name() != null && !tag.name().isBlank())
                .map(TagMergeSnapshot::name)
                .toList();
        Map<String, float[]> embedded = tagEmbeddingCacheService.embedAll(names, libraryId);
        for (TagMergeSnapshot tag : tags) {
            if (tag == null || tag.id() == null || tag.name() == null || tag.name().isBlank()) {
                continue;
            }
            String normalized = tagCanonicalizationService.normalizeDisplayName(tag.name());
            float[] vector = embedded.get(normalized);
            if (vector != null && vector.length > 0) {
                vectors.put(tag.id(), vector);
            }
        }
        return vectors;
    }

    private double groupConfidence(Set<Integer> group, Map<String, Double> pairScores) {
        double max = 0.0;
        List<Integer> ids = new ArrayList<>(group);
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                max = Math.max(max, pairScores.getOrDefault(pairKey(ids.get(i), ids.get(j)), 0.0));
            }
        }
        return max >= HIGH_CONFIDENCE_THRESHOLD ? max : Math.max(max, CLUSTER_THRESHOLD);
    }

    private boolean singlePrefixOnly(String leftName, String rightName) {
        String left = tagCanonicalizationService.normalizeDisplayName(leftName);
        String right = tagCanonicalizationService.normalizeDisplayName(rightName);
        if (left.length() < 2 || right.length() < 2) {
            return false;
        }
        return left.charAt(0) == right.charAt(0) && !left.equals(right)
                && !left.startsWith(right) && !right.startsWith(left);
    }

    private TagMergeSnapshot findTag(List<TagMergeSnapshot> tags, Integer id) {
        return tags.stream()
                .filter(tag -> tag != null && id.equals(tag.id()))
                .findFirst()
                .orElse(null);
    }

    private double cosine(float[] left, float[] right) {
        if (left.length == 0 || right.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String pairKey(Integer leftId, Integer rightId) {
        int min = Math.min(leftId, rightId);
        int max = Math.max(leftId, rightId);
        return min + ":" + max;
    }

    private static final class UnionFind {
        private final Map<Integer, Integer> parent = new HashMap<>();

        private UnionFind(Set<Integer> ids) {
            for (Integer id : ids) {
                parent.put(id, id);
            }
        }

        private Integer find(Integer id) {
            Integer root = parent.get(id);
            if (root == null) {
                return id;
            }
            if (!root.equals(parent.get(root))) {
                parent.put(id, find(parent.get(root)));
            }
            return parent.get(id);
        }

        private void union(Integer left, Integer right) {
            Integer leftRoot = find(left);
            Integer rightRoot = find(right);
            if (!leftRoot.equals(rightRoot)) {
                parent.put(rightRoot, leftRoot);
            }
        }

        private List<Set<Integer>> groups() {
            Map<Integer, Set<Integer>> grouped = new LinkedHashMap<>();
            for (Integer id : parent.keySet()) {
                grouped.computeIfAbsent(find(id), ignored -> new HashSet<>()).add(id);
            }
            return new ArrayList<>(grouped.values());
        }
    }
}
