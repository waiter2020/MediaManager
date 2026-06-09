package com.mediamanager.classification.service;

import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
public class TagCanonicalizationService {

    private static final int MAX_TAG_LENGTH = 64;

    private final TagRepository tagRepository;
    private final TagSimilarityService tagSimilarityService;
    private final TagEmbeddingClusterService tagEmbeddingClusterService;

    @PersistenceContext
    private EntityManager entityManager;

    public TagCanonicalizationService(
            TagRepository tagRepository,
            @Lazy TagSimilarityService tagSimilarityService,
            @Lazy TagEmbeddingClusterService tagEmbeddingClusterService) {
        this.tagRepository = tagRepository;
        this.tagSimilarityService = tagSimilarityService;
        this.tagEmbeddingClusterService = tagEmbeddingClusterService;
    }

    public String normalizeDisplayName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String normalized = Normalizer.normalize(rawName, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cf}\\p{Cc}]+", "")
                .replaceAll("\\s+", " ")
                .strip();
        normalized = normalized.replaceAll("^[#\\s]+", "").strip();
        normalized = normalized.replaceAll("^[\\p{P}\\s]+|[\\p{P}\\s]+$", "").strip();
        if (normalized.length() > MAX_TAG_LENGTH) {
            normalized = normalized.substring(0, MAX_TAG_LENGTH).strip();
        }
        return normalized;
    }

    public String semanticKey(String rawName) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return "";
        }
        return rawSemanticKey(displayName);
    }

    public Optional<String> translateToChinese(String rawName) {
        return Optional.empty();
    }

    public boolean isPreferredChineseName(String rawName) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return false;
        }
        String lower = displayName.toLowerCase(Locale.ROOT);
        return lower.equals(normalizeKnownChineseVariants(lower));
    }

    @Transactional(readOnly = true)
    public Optional<Tag> findEquivalentTag(String rawName) {
        return findEquivalentTag(rawName, null);
    }

    @Transactional(readOnly = true)
    public Optional<Tag> findEquivalentTag(String rawName, Integer libraryId) {
        String key = semanticKey(rawName);
        if (key.isBlank()) {
            return Optional.empty();
        }
        List<Tag> matches = matchingTags(key);
        if (!matches.isEmpty()) {
            return Optional.of(chooseCanonical(matches, normalizeDisplayName(rawName)));
        }

        List<TagMergeSnapshot> snapshots = allTagSnapshots();
        Optional<TagMergeSnapshot> structural = tagSimilarityService.findStructuralMatch(
                rawName, snapshots, MergeAggressiveness.AGGRESSIVE);
        if (structural.isPresent()) {
            return tagRepository.findById(structural.get().id());
        }

        Optional<TagEmbeddingClusterService.NeighborMatch> neighbor =
                tagEmbeddingClusterService.findNearestNeighbor(rawName, snapshots, libraryId);
        if (neighbor.isPresent()) {
            return tagRepository.findById(neighbor.get().tagId());
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<Tag> findCanonicalExisting(String rawName) {
        return findCanonicalExisting(rawName, null);
    }

    @Transactional
    public Optional<Tag> findCanonicalExisting(String rawName, Integer libraryId) {
        String key = semanticKey(rawName);
        if (key.isBlank()) {
            return Optional.empty();
        }
        List<Tag> matches = matchingTags(key);
        if (!matches.isEmpty()) {
            Tag canonical = chooseCanonical(matches, normalizeDisplayName(rawName));
            mergeDuplicates(canonical, matches);
            return Optional.of(canonical);
        }

        Optional<Tag> equivalent = findEquivalentTag(rawName, libraryId);
        if (equivalent.isPresent()) {
            return equivalent;
        }
        return Optional.empty();
    }

    @Transactional
    public Optional<Tag> findOrCreateTag(String rawName, String source, String color) {
        return findOrCreateTag(rawName, source, color, null);
    }

    @Transactional
    public Optional<Tag> findOrCreateTag(String rawName, String source, String color, Integer libraryId) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return Optional.empty();
        }
        Optional<Tag> existing = findCanonicalExisting(displayName, libraryId);
        if (existing.isPresent()) {
            return existing;
        }
        try {
            Tag created = Tag.builder()
                    .name(displayName)
                    .source(source != null && !source.isBlank() ? source : "AUTO")
                    .color(color)
                    .build();
            return Optional.of(tagRepository.saveAndFlush(created));
        } catch (DataIntegrityViolationException e) {
            log.debug("Tag '{}' was created concurrently; resolving existing canonical tag", displayName);
            return findCanonicalExisting(displayName, libraryId);
        }
    }

    @Transactional
    public Optional<String> resolveCanonicalName(String rawName) {
        return resolveCanonicalName(rawName, null);
    }

    @Transactional
    public Optional<String> resolveCanonicalName(String rawName, Integer libraryId) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return Optional.empty();
        }
        return findCanonicalExisting(displayName, libraryId)
                .map(Tag::getName)
                .or(() -> Optional.of(displayName));
    }

    public boolean itemHasEquivalentTag(MediaItem item, String rawName) {
        if (item == null || item.getTags() == null) {
            return false;
        }
        String key = semanticKey(rawName);
        if (key.isBlank()) {
            return false;
        }
        return item.getTags().stream()
                .anyMatch(tag -> key.equals(semanticKey(tag.getName())));
    }

    public boolean addCanonicalTag(MediaItem item, Tag canonicalTag) {
        if (item == null || item.getTags() == null || canonicalTag == null || canonicalTag.getId() == null) {
            return false;
        }
        String key = semanticKey(canonicalTag.getName());
        boolean changed = item.getTags().removeIf(tag ->
                tag != null
                        && tag.getId() != null
                        && !tag.getId().equals(canonicalTag.getId())
                        && key.equals(semanticKey(tag.getName())));
        boolean alreadyAssigned = item.getTags().stream()
                .anyMatch(tag -> tag.getId() != null && tag.getId().equals(canonicalTag.getId()));
        if (!alreadyAssigned) {
            item.getTags().add(canonicalTag);
            changed = true;
        }
        return changed;
    }

    private List<TagMergeSnapshot> allTagSnapshots() {
        List<TagMergeSnapshot> snapshots = new ArrayList<>();
        for (Tag tag : tagRepository.findAll()) {
            if (tag != null && tag.getId() != null) {
                snapshots.add(new TagMergeSnapshot(tag.getId(), tag.getName(), 0L, tag.getSource()));
            }
        }
        return snapshots;
    }

    private List<Tag> matchingTags(String semanticKey) {
        List<Tag> matches = new ArrayList<>();
        for (Tag tag : tagRepository.findAll()) {
            if (tag != null && semanticKey.equals(semanticKey(tag.getName()))) {
                matches.add(tag);
            }
        }
        return matches;
    }

    private Tag chooseCanonical(List<Tag> matches, String requestedDisplayName) {
        return matches.stream()
                .min(Comparator
                        .comparing((Tag tag) -> !isPreferredChineseName(tag.getName()))
                        .thenComparing(tag -> !"MANUAL".equalsIgnoreCase(safe(tag.getSource())))
                        .thenComparing(tag -> !safe(tag.getName()).equals(requestedDisplayName))
                        .thenComparing(tag -> !safe(tag.getName()).equalsIgnoreCase(requestedDisplayName))
                        .thenComparing(tag -> tag.getId() != null ? tag.getId() : Integer.MAX_VALUE))
                .orElse(matches.get(0));
    }

    private void mergeDuplicates(Tag canonical, List<Tag> matches) {
        if (canonical == null || canonical.getId() == null || matches.size() <= 1) {
            return;
        }
        for (Tag duplicate : matches) {
            if (duplicate == null || duplicate.getId() == null || duplicate.getId().equals(canonical.getId())) {
                continue;
            }
            Integer duplicateId = duplicate.getId();
            entityManager.createNativeQuery("""
                    INSERT INTO media_item_tag (media_item_id, tag_id)
                    SELECT media_item_id, :canonicalId FROM media_item_tag WHERE tag_id = :duplicateId
                    ON CONFLICT DO NOTHING
                    """)
                    .setParameter("canonicalId", canonical.getId())
                    .setParameter("duplicateId", duplicateId)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM media_item_tag WHERE tag_id = :duplicateId")
                    .setParameter("duplicateId", duplicateId)
                    .executeUpdate();
            Tag managedDuplicate = entityManager.contains(duplicate) ? duplicate : entityManager.merge(duplicate);
            entityManager.remove(managedDuplicate);
            log.info("Merged duplicate tag {} into canonical tag {}", duplicateId, canonical.getId());
        }
        entityManager.flush();
    }

    static String rawSemanticKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        normalized = normalizeKnownChineseVariants(normalized);
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    static String normalizeKnownChineseVariants(String value) {
        return value
                .replace('\u554f', '\u95ee')
                .replace('\u8a0a', '\u8baf')
                .replace('\u5be9', '\u5ba1')
                .replace('\u5831', '\u62a5')
                .replace('\u5c0e', '\u5bfc')
                .replace('\u7d71', '\u7edf')
                .replace('\u5fa9', '\u590d')
                .replace('\u8a55', '\u8bc4')
                .replace('\u8b77', '\u62a4')
                .replace('\u5e2b', '\u5e08')
                .replace('\u7d81', '\u7ed1')
                .replace('\u7e1b', '\u7f1a')
                .replace('\u7e69', '\u7ef3')
                .replace('\u7d91', '\u6346')
                .replace('\u6230', '\u6218')
                .replace('\u91c1', '\u62e8')
                .replace('\u5ec1', '\u5395')
                .replace('\u52d5', '\u52a8');
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
