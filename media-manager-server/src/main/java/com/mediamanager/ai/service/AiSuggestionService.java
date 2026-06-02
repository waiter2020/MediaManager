package com.mediamanager.ai.service;

import com.mediamanager.ai.entity.AiSuggestion;
import com.mediamanager.ai.repository.AiSuggestionRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.MediaPostProcessService;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SysConfigService;
import com.mediamanager.metadata.service.NfoExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    private final AiSuggestionRepository suggestionRepository;
    private final MediaItemRepository itemRepository;
    private final TagRepository tagRepository;
    private final MediaPostProcessService mediaPostProcessService;
    private final LibraryAccessService libraryAccessService;
    private final NfoExportService nfoExportService;
    private final SysConfigService sysConfigService;

    public AiSuggestionService(
            AiSuggestionRepository suggestionRepository,
            MediaItemRepository itemRepository,
            TagRepository tagRepository,
            @Lazy MediaPostProcessService mediaPostProcessService,
            LibraryAccessService libraryAccessService,
            NfoExportService nfoExportService,
            SysConfigService sysConfigService) {
        this.suggestionRepository = suggestionRepository;
        this.itemRepository = itemRepository;
        this.tagRepository = tagRepository;
        this.mediaPostProcessService = mediaPostProcessService;
        this.libraryAccessService = libraryAccessService;
        this.nfoExportService = nfoExportService;
        this.sysConfigService = sysConfigService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPending() {
        return suggestionRepository.findByReviewStatusOrderByCreatedAtDesc("PENDING").stream()
                .filter(s -> {
                    try {
                        libraryAccessService.assertCanViewItem(s.getMediaItem());
                        return s.getMediaItem() != null && !Boolean.TRUE.equals(s.getMediaItem().getHidden());
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
        if (item == null || Boolean.TRUE.equals(item.getHidden())) {
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        String field = suggestion.getFieldName();
        if ("title".equals(field)) {
            item.setTitle(suggestion.getSuggestedValue());
            itemRepository.save(item);
        } else if ("overview".equals(field)) {
            item.setOverview(suggestion.getSuggestedValue());
            itemRepository.save(item);
        } else if ("originalTitle".equals(field) && suggestion.getSuggestedValue() != null) {
            item.setOriginalTitle(suggestion.getSuggestedValue());
            itemRepository.save(item);
        } else if ("releaseDate".equals(field) && suggestion.getSuggestedValue() != null) {
            try {
                item.setReleaseDate(java.time.LocalDate.parse(suggestion.getSuggestedValue().trim()));
                itemRepository.save(item);
            } catch (DateTimeParseException e) {
                log.warn("Skipping invalid releaseDate value '{}' for item {}: {}",
                        suggestion.getSuggestedValue(), item.getId(), e.getMessage());
            }
        } else if ("rating".equals(field) && suggestion.getSuggestedValue() != null) {
            try {
                java.math.BigDecimal parsedRating = new java.math.BigDecimal(suggestion.getSuggestedValue().trim());
                if (parsedRating.compareTo(java.math.BigDecimal.ZERO) >= 0
                        && parsedRating.compareTo(java.math.BigDecimal.TEN) <= 0) {
                    item.setRating(parsedRating);
                    itemRepository.save(item);
                } else {
                    log.warn("Skipping out-of-range rating {} for item {} (must be 0-10)",
                            parsedRating, item.getId());
                }
            } catch (NumberFormatException ignored) {
                // skip invalid rating
            }
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
        nfoExportService.export(item);
        if (!Boolean.TRUE.equals(item.getHidden())) {
            mediaPostProcessService.syncSearchIndexes(itemRepository.findById(item.getId()).orElse(item));
        }
    }

    @Transactional
    public void reject(Integer id, Integer reviewerId) {
        AiSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(suggestion.getMediaItem());
        MediaItem item = suggestion.getMediaItem();
        if (item == null || Boolean.TRUE.equals(item.getHidden())) {
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        suggestion.setReviewStatus("REJECTED");
        suggestion.setReviewedBy(reviewerId);
        suggestion.setReviewedAt(Instant.now());
        suggestionRepository.save(suggestion);
    }

    @Transactional
    public int batchApprove(List<Integer> ids, Integer reviewerId) {
        if (ids == null) {
            return 0;
        }
        int count = 0;
        for (Integer id : ids) {
            try {
                approve(id, reviewerId);
                count++;
            } catch (BusinessException e) {
                // skip inaccessible or missing
            }
        }
        return count;
    }

    @Transactional
    public int batchReject(List<Integer> ids, Integer reviewerId) {
        if (ids == null) {
            return 0;
        }
        int count = 0;
        for (Integer id : ids) {
            try {
                reject(id, reviewerId);
                count++;
            } catch (BusinessException e) {
                // skip inaccessible or missing
            }
        }
        return count;
    }

    @Transactional
    public void createSuggestion(MediaItem item, String field, String value, String providerId, float confidence) {
        AiSuggestion suggestion = AiSuggestion.builder()
                .mediaItem(item)
                .fieldName(field)
                .suggestedValue(value)
                .providerId(providerId)
                .confidence(confidence)
                .reviewStatus("PENDING")
                .createdAt(Instant.now())
                .build();
        
        suggestion = suggestionRepository.save(suggestion);

        if (shouldAutoApprove(field, confidence)) {
            log.info("Auto-approving AI suggestion for item {} field {} with confidence {}", item.getId(), field, confidence);
            try {
                approve(suggestion.getId(), 0); // 0 representing AI System reviewer
            } catch (Exception e) {
                log.error("Failed to auto-approve AI suggestion {}: {}", suggestion.getId(), e.getMessage());
            }
        }
    }

    private boolean shouldAutoApprove(String field, float confidence) {
        boolean enabled = sysConfigService.getBoolean("ai.auto_approve.enabled", false);
        if (!enabled) {
            return false;
        }

        double threshold = 0.8;
        String thresholdStr = sysConfigService.getString("ai.auto_approve.confidence_threshold", "0.8");
        try {
            threshold = Double.parseDouble(thresholdStr);
        } catch (NumberFormatException e) {
            // ignore
        }
        if (confidence < threshold) {
            return false;
        }

        String allowedFields = sysConfigService.getString("ai.auto_approve.fields", "tag:*,overview");
        if (allowedFields == null || allowedFields.isBlank()) {
            return false;
        }

        if ("*".equals(allowedFields.trim())) {
            return true;
        }

        String[] fieldsArray = allowedFields.split(",");
        for (String f : fieldsArray) {
            String pattern = f.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (field.startsWith(prefix)) {
                    return true;
                }
            } else if (field.equalsIgnoreCase(pattern)) {
                return true;
            }
        }
        return false;
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
        map.put("rawPayload", Map.of(
                "fieldName", s.getFieldName(),
                "suggestedValue", s.getSuggestedValue(),
                "providerId", s.getProviderId(),
                "confidence", s.getConfidence()));
        return map;
    }
}
