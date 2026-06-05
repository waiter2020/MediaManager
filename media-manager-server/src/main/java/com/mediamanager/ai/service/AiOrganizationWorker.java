package com.mediamanager.ai.service;

import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import com.mediamanager.classification.entity.Tag;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiOrganizationWorker {

    private final TagRepository tagRepository;
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
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean mergeTag(Integer canonicalId, Integer duplicateId) {
        if (canonicalId == null || duplicateId == null || canonicalId.equals(duplicateId)
                || !tagRepository.existsById(canonicalId) || !tagRepository.existsById(duplicateId)) {
            return false;
        }
        entityManager.createNativeQuery("""
                INSERT OR IGNORE INTO media_item_tag (media_item_id, tag_id)
                SELECT media_item_id, :canonicalId FROM media_item_tag WHERE tag_id = :duplicateId
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
            AiOrganizationResponse.TagUsage candidate) {
        if (candidate == null || candidate.getId() == null || !tagRepository.existsById(candidate.getId())) {
            return notCreated(candidate);
        }
        SysUser user = securityCurrentUser.requireCurrentUser();
        String name = "AI - " + candidate.getName();
        if (collectionRepository.existsByOwner_IdAndNameIgnoreCase(user.getId(), name)) {
            return notCreated(candidate);
        }

        MediaCollectionRuleDto rule = new MediaCollectionRuleDto();
        rule.setLibraryId(request.getLibraryId());
        rule.setTagIds(List.of(candidate.getId()));
        rule.setLimit(request.getCollectionItemLimit());
        rule.setSortField("rating");
        rule.setSortOrder("DESC");

        MediaCollectionCreateRequest createRequest = new MediaCollectionCreateRequest();
        createRequest.setName(name);
        createRequest.setDescription("Smart collection for tag " + candidate.getName());
        createRequest.setType("COLLECTION");
        createRequest.setVisibility("PRIVATE");
        createRequest.setSmart(true);
        createRequest.setRule(rule);

        MediaCollectionResponse created = mediaCollectionService.createCollection(createRequest);
        return AiOrganizationResponse.GeneratedCollection.builder()
                .id(created.getId())
                .name(created.getName())
                .tagId(candidate.getId())
                .tagName(candidate.getName())
                .itemCount((long) (created.getItemCount() != null ? created.getItemCount() : 0))
                .created(true)
                .build();
    }

    private AiOrganizationResponse.GeneratedCollection notCreated(AiOrganizationResponse.TagUsage candidate) {
        return AiOrganizationResponse.GeneratedCollection.builder()
                .name(candidate != null ? "AI - " + candidate.getName() : "AI collection")
                .tagId(candidate != null ? candidate.getId() : null)
                .tagName(candidate != null ? candidate.getName() : null)
                .itemCount(candidate != null ? candidate.getUsageCount() : 0L)
                .created(false)
                .build();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
