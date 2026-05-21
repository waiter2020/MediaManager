package com.mediamanager.ai.service;

import com.mediamanager.ai.entity.AiSuggestion;
import com.mediamanager.ai.repository.AiSuggestionRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.search.service.FtsIndexService;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private final AiSuggestionRepository suggestionRepository;
    private final MediaItemRepository itemRepository;
    private final TagRepository tagRepository;
    private final FtsIndexService ftsIndexService;
    private final LibraryAccessService libraryAccessService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPending() {
        return suggestionRepository.findByReviewStatusOrderByCreatedAtDesc("PENDING").stream()
                .filter(s -> {
                    try {
                        libraryAccessService.assertCanViewItem(s.getMediaItem());
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                })
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public void approve(Integer id, Integer reviewerId) {
        AiSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(suggestion.getMediaItem());
        MediaItem item = suggestion.getMediaItem();
        String field = suggestion.getFieldName();
        if ("title".equals(field)) {
            item.setTitle(suggestion.getSuggestedValue());
            itemRepository.save(item);
        } else if ("overview".equals(field)) {
            item.setOverview(suggestion.getSuggestedValue());
            itemRepository.save(item);
        } else if (field != null && field.startsWith("tag:")) {
            String resolvedTagName = field.substring(4);
            if (suggestion.getSuggestedValue() != null && !suggestion.getSuggestedValue().isBlank()) {
                resolvedTagName = suggestion.getSuggestedValue();
            }
            final String tagName = resolvedTagName;
            Tag tag = tagRepository.findByName(tagName).orElseGet(() -> {
                Tag created = new Tag();
                created.setName(tagName);
                created.setSource("AI");
                created.setColor("#8b5cf6");
                return tagRepository.save(created);
            });
            if (!item.getTags().contains(tag)) {
                item.getTags().add(tag);
                itemRepository.save(item);
            }
        }
        suggestion.setReviewStatus("APPROVED");
        suggestion.setReviewedBy(reviewerId);
        suggestion.setReviewedAt(Instant.now());
        suggestionRepository.save(suggestion);
        if (!Boolean.TRUE.equals(item.getHidden())) {
            ftsIndexService.indexItem(itemRepository.findById(item.getId()).orElse(item));
        }
    }

    @Transactional
    public void reject(Integer id, Integer reviewerId) {
        AiSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(suggestion.getMediaItem());
        suggestion.setReviewStatus("REJECTED");
        suggestion.setReviewedBy(reviewerId);
        suggestion.setReviewedAt(Instant.now());
        suggestionRepository.save(suggestion);
    }

    @Transactional
    public void createSuggestion(MediaItem item, String field, String value, String providerId, float confidence) {
        suggestionRepository.save(AiSuggestion.builder()
                .mediaItem(item)
                .fieldName(field)
                .suggestedValue(value)
                .providerId(providerId)
                .confidence(confidence)
                .reviewStatus("PENDING")
                .createdAt(Instant.now())
                .build());
    }

    private Map<String, Object> toMap(AiSuggestion s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("mediaItemId", s.getMediaItem().getId());
        map.put("mediaTitle", s.getMediaItem().getTitle());
        map.put("fieldName", s.getFieldName());
        map.put("suggestedValue", s.getSuggestedValue() != null ? s.getSuggestedValue() : "");
        map.put("providerId", s.getProviderId() != null ? s.getProviderId() : "");
        map.put("confidence", s.getConfidence() != null ? s.getConfidence() : 0f);
        map.put("reviewStatus", s.getReviewStatus());
        return map;
    }
}
