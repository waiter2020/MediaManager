package com.mediamanager.classification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TagSimilarityService {

    private static final Pattern SUFFIX_STRIP = Pattern.compile("(位|式|姿势|体位|拍摄|片|风格)$");
    private static final Set<String> INSTRUMENT_SUFFIXES = Set.of("\u68d2", "\u5668", "\u5177");
    private static final int MIN_PREFIX_LENGTH = 2;
    private static final int HIGH_CONFIDENCE_LENGTH_DELTA = 3;

    private final TagCanonicalizationService tagCanonicalizationService;

    public record SimilarTagCluster(
            Integer canonicalId,
            List<Integer> memberIds,
            double confidence,
            String reason) {
    }

    public List<SimilarTagCluster> clusterByStructure(
            List<TagMergeSnapshot> tags,
            MergeAggressiveness mode) {
        if (tags == null || tags.isEmpty() || mode == MergeAggressiveness.CONSERVATIVE) {
            return List.of();
        }
        Map<Integer, TagMergeSnapshot> byId = index(tags);
        UnionFind unionFind = new UnionFind(byId.keySet());
        Map<String, String> mergeReasons = new HashMap<>();

        for (int i = 0; i < tags.size(); i++) {
            TagMergeSnapshot left = tags.get(i);
            String leftName = normalizedName(left);
            if (leftName.isBlank()) {
                continue;
            }
            for (int j = i + 1; j < tags.size(); j++) {
                TagMergeSnapshot right = tags.get(j);
                String rightName = normalizedName(right);
                if (rightName.isBlank()) {
                    continue;
                }
                Optional<String> reason = structuralMergeReason(leftName, rightName, mode);
                if (reason.isPresent()) {
                    unionFind.union(left.id(), right.id());
                    mergeReasons.put(pairKey(left.id(), right.id()), reason.get());
                }
            }
        }

        List<SimilarTagCluster> clusters = new ArrayList<>();
        for (Set<Integer> group : unionFind.groups()) {
            if (group.size() < 2) {
                continue;
            }
            List<TagMergeSnapshot> members = group.stream()
                    .map(byId::get)
                    .sorted(canonicalOrder())
                    .toList();
            TagMergeSnapshot canonical = members.getFirst();
            List<Integer> duplicateIds = members.stream()
                    .skip(1)
                    .map(TagMergeSnapshot::id)
                    .toList();
            double confidence = clusterConfidence(members, mergeReasons);
            String reason = summarizeReason(members, mergeReasons);
            clusters.add(new SimilarTagCluster(canonical.id(), duplicateIds, confidence, reason));
        }
        return clusters;
    }

    public Optional<TagMergeSnapshot> findStructuralMatch(
            String rawName,
            List<TagMergeSnapshot> candidates,
            MergeAggressiveness mode) {
        String name = normalizedName(rawName);
        if (name.isBlank() || candidates == null || candidates.isEmpty()
                || mode == MergeAggressiveness.CONSERVATIVE) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && candidate.id() != null)
                .filter(candidate -> structuralMergeReason(name, normalizedName(candidate), mode).isPresent())
                .min(canonicalOrder());
    }

    public boolean shouldBlockMerge(String leftName, String rightName) {
        String left = normalizedName(leftName);
        String right = normalizedName(rightName);
        if (left.isBlank() || right.isBlank()) {
            return true;
        }
        if (compoundBlocked(left, right)) {
            return true;
        }
        return instrumentBlocked(left, right);
    }

    private Optional<String> structuralMergeReason(
            String leftName,
            String rightName,
            MergeAggressiveness mode) {
        if (leftName.equals(rightName) || shouldBlockMerge(leftName, rightName)) {
            return Optional.empty();
        }
        String shorter = leftName.length() <= rightName.length() ? leftName : rightName;
        String longer = leftName.length() > rightName.length() ? leftName : rightName;

        if (mode != MergeAggressiveness.CONSERVATIVE
                && shorter.length() >= MIN_PREFIX_LENGTH
                && longer.startsWith(shorter)) {
            return Optional.of("prefix");
        }

        if (mode == MergeAggressiveness.AGGRESSIVE
                && semanticBase(leftName).equals(semanticBase(rightName))
                && !semanticBase(leftName).isBlank()) {
            return Optional.of("suffix-normalized");
        }

        if (mode == MergeAggressiveness.AGGRESSIVE
                && shorter.length() <= 6
                && longer.length() <= 6
                && similarityRatio(shorter, longer) >= 0.8) {
            return Optional.of("edit-distance");
        }
        return Optional.empty();
    }

    private boolean compoundBlocked(String left, String right) {
        if (left.length() < 2 || right.length() < 2) {
            return false;
        }
        return left.charAt(0) == right.charAt(0) && left.charAt(1) != right.charAt(1);
    }

    private boolean instrumentBlocked(String left, String right) {
        String shorter = left.length() <= right.length() ? left : right;
        String longer = left.length() > right.length() ? left : right;
        if (!longer.startsWith(shorter) || longer.length() <= shorter.length()) {
            return false;
        }
        String suffix = longer.substring(shorter.length());
        return INSTRUMENT_SUFFIXES.stream().anyMatch(suffix::startsWith);
    }

    private String semanticBase(String name) {
        return tagCanonicalizationService.semanticKey(SUFFIX_STRIP.matcher(name).replaceAll(""));
    }

    private double clusterConfidence(List<TagMergeSnapshot> members, Map<String, String> mergeReasons) {
        if (members.size() < 2) {
            return 0.88;
        }
        String leftName = normalizedName(members.get(0));
        String rightName = normalizedName(members.get(1));
        String shorter = leftName.length() <= rightName.length() ? leftName : rightName;
        String longer = leftName.length() > rightName.length() ? leftName : rightName;
        if (longer.startsWith(shorter)
                && shorter.length() >= MIN_PREFIX_LENGTH
                && longer.length() - shorter.length() <= HIGH_CONFIDENCE_LENGTH_DELTA) {
            return 0.95;
        }
        return 0.88;
    }

    private String summarizeReason(List<TagMergeSnapshot> members, Map<String, String> mergeReasons) {
        if (members.size() < 2) {
            return "structure";
        }
        Integer leftId = members.get(0).id();
        Integer rightId = members.get(1).id();
        return mergeReasons.getOrDefault(pairKey(leftId, rightId),
                mergeReasons.getOrDefault(pairKey(rightId, leftId), "structure"));
    }

    private Comparator<TagMergeSnapshot> canonicalOrder() {
        return Comparator
                .comparing((TagMergeSnapshot tag) -> -(tag.usageCount() != null ? tag.usageCount() : 0L))
                .thenComparing(tag -> !"MANUAL".equalsIgnoreCase(safe(tag.source())))
                .thenComparing(tag -> -safe(tag.name()).length())
                .thenComparing(tag -> tag.id() != null ? tag.id() : Integer.MAX_VALUE);
    }

    private Map<Integer, TagMergeSnapshot> index(List<TagMergeSnapshot> tags) {
        Map<Integer, TagMergeSnapshot> byId = new LinkedHashMap<>();
        for (TagMergeSnapshot tag : tags) {
            if (tag != null && tag.id() != null) {
                byId.put(tag.id(), tag);
            }
        }
        return byId;
    }

    private String normalizedName(TagMergeSnapshot tag) {
        return normalizedName(tag != null ? tag.name() : null);
    }

    private String normalizedName(String rawName) {
        return tagCanonicalizationService.normalizeDisplayName(rawName);
    }

    private String pairKey(Integer leftId, Integer rightId) {
        int min = Math.min(leftId, rightId);
        int max = Math.max(leftId, rightId);
        return min + ":" + max;
    }

    private double similarityRatio(String left, String right) {
        int distance = levenshtein(left, right);
        int maxLen = Math.max(left.length(), right.length());
        if (maxLen == 0) {
            return 1.0;
        }
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshtein(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[left.length()][right.length()];
    }

    private String safe(String value) {
        return value != null ? value : "";
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
