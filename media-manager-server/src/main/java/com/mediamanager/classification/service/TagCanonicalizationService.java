package com.mediamanager.classification.service;

import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagCanonicalizationService {

    private static final int MAX_TAG_LENGTH = 64;
    private static final Map<String, String> SYNONYM_KEYS = buildSynonymKeys();
    private static final Map<String, String> CHINESE_NAMES_BY_KEY = buildChineseNamesByKey();

    private final TagRepository tagRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public String normalizeDisplayName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String normalized = Normalizer.normalize(rawName, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .strip();
        normalized = normalized.replaceAll("^[#\\s]+", "").strip();
        normalized = normalized.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").strip();
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
        String key = rawSemanticKey(displayName);
        return SYNONYM_KEYS.getOrDefault(key, key);
    }

    public Optional<String> translateToChinese(String rawName) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return Optional.empty();
        }
        String translated = CHINESE_NAMES_BY_KEY.get(semanticKey(displayName));
        if (translated == null || translated.isBlank() || translated.equals(displayName)) {
            return Optional.empty();
        }
        return Optional.of(translated);
    }

    public boolean isPreferredChineseName(String rawName) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return false;
        }
        String translated = CHINESE_NAMES_BY_KEY.get(semanticKey(displayName));
        return translated == null || translated.equals(displayName);
    }

    @Transactional(readOnly = true)
    public Optional<Tag> findEquivalentTag(String rawName) {
        String key = semanticKey(rawName);
        if (key.isBlank()) {
            return Optional.empty();
        }
        List<Tag> matches = matchingTags(key);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(chooseCanonical(matches, normalizeDisplayName(rawName)));
    }

    @Transactional
    public Optional<Tag> findCanonicalExisting(String rawName) {
        String key = semanticKey(rawName);
        if (key.isBlank()) {
            return Optional.empty();
        }
        List<Tag> matches = matchingTags(key);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        Tag canonical = chooseCanonical(matches, normalizeDisplayName(rawName));
        mergeDuplicates(canonical, matches);
        return Optional.of(canonical);
    }

    @Transactional
    public Optional<Tag> findOrCreateTag(String rawName, String source, String color) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return Optional.empty();
        }
        Optional<Tag> existing = findCanonicalExisting(displayName);
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
            return findCanonicalExisting(displayName);
        }
    }

    @Transactional
    public Optional<String> resolveCanonicalName(String rawName) {
        String displayName = normalizeDisplayName(rawName);
        if (displayName.isBlank()) {
            return Optional.empty();
        }
        return findCanonicalExisting(displayName)
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
                    INSERT OR IGNORE INTO media_item_tag (media_item_id, tag_id)
                    SELECT media_item_id, :canonicalId FROM media_item_tag WHERE tag_id = :duplicateId
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

    private static Map<String, String> buildSynonymKeys() {
        Map<String, String> map = new HashMap<>();
        register(map, "sciencefiction", "sci-fi", "scifi", "sci fi", "\u79d1\u5e7b", "\u79d1\u5e7b\u7247");
        register(map, "action", "\u52a8\u4f5c", "\u52a8\u4f5c\u7247");
        register(map, "comedy", "\u559c\u5267", "\u559c\u5267\u7247");
        register(map, "drama", "\u5267\u60c5", "\u5267\u60c5\u7247");
        register(map, "romance", "romantic", "\u7231\u60c5", "\u7231\u60c5\u7247");
        register(map, "horror", "\u6050\u6016", "\u6050\u6016\u7247");
        register(map, "thriller", "\u60ca\u609a", "\u60ca\u609a\u7247");
        register(map, "mystery", "suspense", "\u60ac\u7591", "\u63a8\u7406");
        register(map, "documentary", "doc", "\u7eaa\u5f55", "\u7eaa\u5f55\u7247");
        register(map, "animation", "anime", "animated", "\u52a8\u753b", "\u52a8\u6f2b", "\u52a8\u753b\u7247");
        register(map, "fantasy", "\u5947\u5e7b", "\u9b54\u5e7b");
        register(map, "adventure", "\u5192\u9669");
        register(map, "crime", "\u72af\u7f6a");
        register(map, "war", "\u6218\u4e89");
        register(map, "history", "historical", "\u5386\u53f2");
        register(map, "music", "musical", "\u97f3\u4e50", "\u6b4c\u821e");
        register(map, "sports", "sport", "\u4f53\u80b2", "\u8fd0\u52a8");
        register(map, "biography", "biopic", "\u4f20\u8bb0");
        register(map, "western", "\u897f\u90e8");
        register(map, "family", "\u5bb6\u5ead");
        register(map, "children", "childrens", "kids", "\u513f\u7ae5");
        register(map, "short", "\u77ed\u7247");
        register(map, "4k", "uhd", "ultrahd", "ultra hd", "ultra high definition", "\u8d85\u9ad8\u6e05");
        register(map, "1080p", "fullhd", "full hd", "fhd", "\u5168\u9ad8\u6e05");
        register(map, "720p", "hd", "\u9ad8\u6e05");
        register(map, "h264", "h.264", "avc");
        register(map, "h265", "h.265", "hevc");
        return Map.copyOf(map);
    }

    private static Map<String, String> buildChineseNamesByKey() {
        Map<String, String> map = new HashMap<>();
        registerChinese(map, "sciencefiction", "\u79d1\u5e7b");
        registerChinese(map, "action", "\u52a8\u4f5c");
        registerChinese(map, "comedy", "\u559c\u5267");
        registerChinese(map, "drama", "\u5267\u60c5");
        registerChinese(map, "romance", "\u7231\u60c5");
        registerChinese(map, "horror", "\u6050\u6016");
        registerChinese(map, "thriller", "\u60ca\u609a");
        registerChinese(map, "mystery", "\u60ac\u7591");
        registerChinese(map, "documentary", "\u7eaa\u5f55\u7247");
        registerChinese(map, "animation", "\u52a8\u753b");
        registerChinese(map, "fantasy", "\u5947\u5e7b");
        registerChinese(map, "adventure", "\u5192\u9669");
        registerChinese(map, "crime", "\u72af\u7f6a");
        registerChinese(map, "war", "\u6218\u4e89");
        registerChinese(map, "history", "\u5386\u53f2");
        registerChinese(map, "music", "\u97f3\u4e50");
        registerChinese(map, "sports", "\u4f53\u80b2");
        registerChinese(map, "biography", "\u4f20\u8bb0");
        registerChinese(map, "western", "\u897f\u90e8");
        registerChinese(map, "family", "\u5bb6\u5ead");
        registerChinese(map, "children", "\u513f\u7ae5");
        registerChinese(map, "short", "\u77ed\u7247");
        registerChinese(map, "4k", "\u8d85\u9ad8\u6e05");
        registerChinese(map, "1080p", "\u5168\u9ad8\u6e05");
        registerChinese(map, "720p", "\u9ad8\u6e05");
        registerChinese(map, "adult", "\u6210\u4eba");
        registerChinese(map, "japanese", "\u65e5\u672c");
        registerChinese(map, "korean", "\u97e9\u56fd");
        registerChinese(map, "chinese", "\u4e2d\u56fd");
        registerChinese(map, "american", "\u7f8e\u56fd");
        registerChinese(map, "british", "\u82f1\u56fd");
        registerChinese(map, "independent", "\u72ec\u7acb");
        registerChinese(map, "classic", "\u7ecf\u5178");
        registerChinese(map, "cult", "\u90aa\u5178");
        registerChinese(map, "noir", "\u9ed1\u8272\u7535\u5f71");
        registerChinese(map, "superhero", "\u8d85\u7ea7\u82f1\u96c4");
        registerChinese(map, "disaster", "\u707e\u96be");
        registerChinese(map, "roadmovie", "\u516c\u8def\u7247");
        registerChinese(map, "comingofage", "\u6210\u957f");
        registerChinese(map, "sliceoflife", "\u65e5\u5e38");
        registerChinese(map, "psychological", "\u5fc3\u7406");
        registerChinese(map, "political", "\u653f\u6cbb");
        registerChinese(map, "period", "\u5e74\u4ee3");
        registerChinese(map, "martialarts", "\u6b66\u4fa0");
        registerChinese(map, "wuxia", "\u6b66\u4fa0");
        registerChinese(map, "kungfu", "\u529f\u592b");
        registerChinese(map, "food", "\u7f8e\u98df");
        registerChinese(map, "travel", "\u65c5\u884c");
        registerChinese(map, "education", "\u6559\u80b2");
        registerChinese(map, "technology", "\u79d1\u6280");
        registerChinese(map, "nature", "\u81ea\u7136");
        registerChinese(map, "wildlife", "\u91ce\u751f\u52a8\u7269");
        registerChinese(map, "concert", "\u6f14\u5531\u4f1a");
        registerChinese(map, "reality", "\u771f\u4eba\u79c0");
        registerChinese(map, "talkshow", "\u8bbf\u8c08");
        registerChinese(map, "variety", "\u7efc\u827a");
        registerChinese(map, "standup", "\u8131\u53e3\u79c0");
        return Map.copyOf(map);
    }

    private static void registerChinese(Map<String, String> map, String key, String chineseName) {
        String semantic = SYNONYM_KEYS.getOrDefault(rawSemanticKey(key), rawSemanticKey(key));
        map.put(semantic, chineseName);
    }

    private static void register(Map<String, String> map, String canonical, String... aliases) {
        String canonicalKey = rawSemanticKey(canonical);
        map.put(canonicalKey, canonicalKey);
        for (String alias : aliases) {
            map.put(rawSemanticKey(alias), canonicalKey);
        }
    }

    private static String rawSemanticKey(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT);
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
