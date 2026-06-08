package com.mediamanager.ai.service;

import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagColorService;
import com.mediamanager.media.dto.MediaCollectionCreateRequest;
import com.mediamanager.media.dto.MediaCollectionResponse;
import com.mediamanager.media.dto.MediaCollectionRuleDto;
import com.mediamanager.media.repository.MediaCollectionRepository;
import com.mediamanager.media.service.MediaCollectionService;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.common.security.SecurityCurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiOrganizationWorker {

    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagColorService tagColorService;
    private final MediaCollectionService mediaCollectionService;
    private final MediaCollectionRepository collectionRepository;
    private final SecurityCurrentUser securityCurrentUser;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Integer> listTagIds() {
        return tagRepository.findAllIds();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<TagSnapshot> listTagSnapshots() {
        return tagRepository.findAll().stream()
                .filter(tag -> tag != null && tag.getId() != null)
                .map(tag -> new TagSnapshot(tag.getId(), tag.getName()))
                .sorted(Comparator.comparing(
                        snapshot -> safe(snapshot.name()),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mergeTag(Integer canonicalId, Integer duplicateId) {
        if (canonicalId == null || duplicateId == null || canonicalId.equals(duplicateId)
                || !tagRepository.existsById(canonicalId) || !tagRepository.existsById(duplicateId)) {
            return false;
        }
        entityManager.createNativeQuery("""
                INSERT INTO media_item_tag (media_item_id, tag_id)
                SELECT media_item_id, :canonicalId FROM media_item_tag WHERE tag_id = :duplicateId
                ON CONFLICT DO NOTHING
                """)
                .setParameter("canonicalId", canonicalId)
                .setParameter("duplicateId", duplicateId)
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM media_item_tag WHERE tag_id = :duplicateId")
                .setParameter("duplicateId", duplicateId)
                .executeUpdate();
        int deleted = entityManager.createNativeQuery("DELETE FROM tag WHERE id = :duplicateId")
                .setParameter("duplicateId", duplicateId)
                .executeUpdate();
        return deleted > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteTag(Integer tagId) {
        if (tagId == null || !tagRepository.existsById(tagId)) {
            return false;
        }
        entityManager.createNativeQuery("DELETE FROM media_item_tag WHERE tag_id = :tagId")
                .setParameter("tagId", tagId)
                .executeUpdate();
        return entityManager.createNativeQuery("DELETE FROM tag WHERE id = :tagId")
                .setParameter("tagId", tagId)
                .executeUpdate() > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean translateTag(Integer tagId) {
        Tag tag = tagRepository.findById(tagId).orElse(null);
        if (tag == null) {
            return false;
        }
        String currentName = safe(tag.getName());
        String translatedName = tagCanonicalizationService.translateToChinese(currentName).orElse("");
        return renameOrMergeTag(tag, currentName, translatedName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean translateTag(Integer tagId, String translatedName) {
        Tag tag = tagRepository.findById(tagId).orElse(null);
        if (tag == null) {
            return false;
        }
        return renameOrMergeTag(tag, safe(tag.getName()), translatedName);
    }

    private boolean renameOrMergeTag(Tag tag, String currentName, String translatedName) {
        translatedName = tagCanonicalizationService.normalizeDisplayName(translatedName);
        if (translatedName.isBlank()
                || translatedName.equals(currentName)
                || translatedName.equalsIgnoreCase(currentName)) {
            return false;
        }
        Tag existingChineseTag = tagRepository.findByName(translatedName).orElse(null);
        if (existingChineseTag != null && existingChineseTag.getId() != null) {
            if (existingChineseTag.getId().equals(tag.getId())) {
                return false;
            }
            entityManager.detach(tag);
            entityManager.detach(existingChineseTag);
            return mergeTag(existingChineseTag.getId(), tag.getId());
        }
        tag.setName(translatedName);
        tag.setColor(tagColorService.colorFor(translatedName));
        return true;
    }

    public record TagSnapshot(Integer id, String name) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recolorTag(Integer tagId, boolean recolorManualTags) {
        Tag tag = tagRepository.findById(tagId).orElse(null);
        if (tag == null || !tagColorService.shouldRecolor(tag, recolorManualTags)) {
            return false;
        }
        String nextColor = tagColorService.colorFor(tag.getName());
        if (nextColor.equalsIgnoreCase(safe(tag.getColor()))) {
            return false;
        }
        tag.setColor(nextColor);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiOrganizationResponse.GeneratedCollection createSmartCollection(
            AiOrganizationRequest request,
            AiOrganizationResponse.SmartCollectionCandidate candidate) {
        if (!isValidCollectionCandidate(candidate)) {
            return notCreated(candidate);
        }
        SysUser user = securityCurrentUser.requireCurrentUser();
        String name = candidate.getName();
        if (collectionRepository.existsByOwner_IdAndNameIgnoreCase(user.getId(), name)) {
            return notCreated(candidate);
        }

        MediaCollectionRuleDto rule = ruleForCandidate(request, candidate);

        MediaCollectionCreateRequest createRequest = new MediaCollectionCreateRequest();
        createRequest.setName(name);
        createRequest.setDescription("AI smart collection for "
                + safe(candidate.getDimensionLabel()) + " " + safe(candidate.getDisplayValue()));
        createRequest.setType("COLLECTION");
        createRequest.setVisibility("PRIVATE");
        createRequest.setSmart(true);
        createRequest.setRule(rule);

        MediaCollectionResponse created = mediaCollectionService.createCollection(createRequest);
        return AiOrganizationResponse.GeneratedCollection.builder()
                .id(created.getId())
                .name(created.getName())
                .dimension(candidate.getDimension())
                .dimensionLabel(candidate.getDimensionLabel())
                .value(candidate.getValue())
                .displayValue(candidate.getDisplayValue())
                .tagId(candidate.getTagId())
                .tagName(candidate.getTagName())
                .categoryId(candidate.getCategoryId())
                .categoryName(candidate.getCategoryName())
                .metadataField(candidate.getMetadataField())
                .metadataValue(candidate.getMetadataValue())
                .itemCount((long) (created.getItemCount() != null ? created.getItemCount() : 0))
                .created(true)
                .build();
    }

    private boolean isValidCollectionCandidate(AiOrganizationResponse.SmartCollectionCandidate candidate) {
        if (candidate == null || candidate.getName() == null || candidate.getName().isBlank()) {
            return false;
        }
        String dimension = safe(candidate.getDimension());
        if ("TAG".equals(dimension)) {
            return candidate.getTagId() != null && tagRepository.existsById(candidate.getTagId());
        }
        if ("CATEGORY".equals(dimension)) {
            return candidate.getCategoryId() != null && categoryRepository.existsById(candidate.getCategoryId());
        }
        if (candidate.getMetadataField() != null && candidate.getMetadataValue() != null) {
            return !candidate.getMetadataField().isBlank() && !candidate.getMetadataValue().isBlank();
        }
        return "TYPE".equals(dimension) && candidate.getValue() != null && !candidate.getValue().isBlank();
    }

    private MediaCollectionRuleDto ruleForCandidate(
            AiOrganizationRequest request,
            AiOrganizationResponse.SmartCollectionCandidate candidate) {
        MediaCollectionRuleDto rule = new MediaCollectionRuleDto();
        rule.setLibraryId(request.getLibraryId());
        rule.setLimit(request.getCollectionItemLimit());
        rule.setSortField(sortFieldForCandidate(candidate));
        rule.setSortOrder("DESC");

        String dimension = safe(candidate.getDimension());
        if ("TYPE".equals(dimension)) {
            rule.setType(candidate.getValue());
        } else if ("TAG".equals(dimension)) {
            rule.setTagIds(List.of(candidate.getTagId()));
        } else if ("CATEGORY".equals(dimension)) {
            rule.setCategoryIds(List.of(candidate.getCategoryId()));
        } else {
            rule.setMetadataField(candidate.getMetadataField());
            rule.setMetadataValue(candidate.getMetadataValue());
        }
        return rule;
    }

    private String sortFieldForCandidate(AiOrganizationResponse.SmartCollectionCandidate candidate) {
        String value = safe(candidate != null ? candidate.getValue() : null);
        if ("IMAGE".equalsIgnoreCase(value) || "AUDIO".equalsIgnoreCase(value)) {
            return "createdAt";
        }
        return "rating";
    }

    private AiOrganizationResponse.GeneratedCollection notCreated(
            AiOrganizationResponse.SmartCollectionCandidate candidate) {
        return AiOrganizationResponse.GeneratedCollection.builder()
                .name(candidate != null ? candidate.getName() : "AI collection")
                .dimension(candidate != null ? candidate.getDimension() : null)
                .dimensionLabel(candidate != null ? candidate.getDimensionLabel() : null)
                .value(candidate != null ? candidate.getValue() : null)
                .displayValue(candidate != null ? candidate.getDisplayValue() : null)
                .tagId(candidate != null ? candidate.getTagId() : null)
                .tagName(candidate != null ? candidate.getTagName() : null)
                .categoryId(candidate != null ? candidate.getCategoryId() : null)
                .categoryName(candidate != null ? candidate.getCategoryName() : null)
                .metadataField(candidate != null ? candidate.getMetadataField() : null)
                .metadataValue(candidate != null ? candidate.getMetadataValue() : null)
                .itemCount(candidate != null ? candidate.getUsageCount() : 0L)
                .created(false)
                .build();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
