package com.mediamanager.ai.service;

import com.mediamanager.ai.entity.AiSuggestion;
import com.mediamanager.ai.repository.AiSuggestionRepository;
import com.mediamanager.classification.service.TagColorService;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.MediaPostProcessService;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SysConfigService;
import com.mediamanager.metadata.service.NfoExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);
    private static final int AI_SYSTEM_REVIEWER_ID = 0;
    private static final int AUTO_APPROVE_PAGE_SIZE = 200;
    private static final int APPROVE_ALL_PAGE_SIZE = 100;

    private final AiSuggestionRepository suggestionRepository;
    private final MediaItemRepository itemRepository;
    private final TagCanonicalizationService tagCanonicalizationService;
    private final TagQualityService tagQualityService;
    private final TagColorService tagColorService;
    private final MediaPostProcessService mediaPostProcessService;
    private final LibraryAccessService libraryAccessService;
    private final NfoExportService nfoExportService;
    private final SysConfigService sysConfigService;

    public AiSuggestionService(
            AiSuggestionRepository suggestionRepository,
            MediaItemRepository itemRepository,
            TagCanonicalizationService tagCanonicalizationService,
            TagQualityService tagQualityService,
            TagColorService tagColorService,
            @Lazy MediaPostProcessService mediaPostProcessService,
            LibraryAccessService libraryAccessService,
            NfoExportService nfoExportService,
            SysConfigService sysConfigService) {
        this.suggestionRepository = suggestionRepository;
        this.itemRepository = itemRepository;
        this.tagCanonicalizationService = tagCanonicalizationService;
        this.tagQualityService = tagQualityService;
        this.tagColorService = tagColorService;
        this.mediaPostProcessService = mediaPostProcessService;
        this.libraryAccessService = libraryAccessService;
        this.nfoExportService = nfoExportService;
        this.sysConfigService = sysConfigService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPending() {
        return listPending(1, 20).getItems();
    }

    @Transactional(readOnly = true)
    public PageResult<Map<String, Object>> listPending(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(null);
        if (libraryIds.isEmpty()) {
            return PageResult.of(List.of(), 0, safePage, safeSize);
        }
        Page<AiSuggestion> suggestionPage = suggestionRepository.findVisiblePendingPage(
                "PENDING",
                libraryIds,
                PageRequest.of(safePage - 1, safeSize));
        List<Map<String, Object>> items = suggestionPage.getContent().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
        return PageResult.of(items, suggestionPage.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public void approve(Integer id, Integer reviewerId) {
        AiSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        applySuggestion(suggestion, reviewerId, true);
    }

    private void applySuggestion(AiSuggestion suggestion, Integer reviewerId, boolean enforceAccess) {
        if (enforceAccess) {
            libraryAccessService.assertCanViewItem(suggestion.getMediaItem());
        }
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
            tagCanonicalizationService.findOrCreateTag(resolvedTagName, "AI", tagColorService.colorFor(resolvedTagName))
                    .ifPresent(tag -> {
                        if (tagCanonicalizationService.addCanonicalTag(item, tag)) {
                            itemRepository.save(item);
                        }
                    });
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
    public int approveAllPending(Integer reviewerId) {
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(null);
        if (libraryIds.isEmpty()) {
            return 0;
        }
        int approved = 0;
        while (true) {
            List<AiSuggestion> suggestions = suggestionRepository.findVisiblePendingBatch(
                    "PENDING",
                    libraryIds,
                    PageRequest.of(0, APPROVE_ALL_PAGE_SIZE));
            if (suggestions.isEmpty()) {
                break;
            }
            int approvedBeforeBatch = approved;
            for (AiSuggestion suggestion : suggestions) {
                try {
                    applySuggestion(suggestion, reviewerId, false);
                    approved++;
                } catch (BusinessException e) {
                    // skip inaccessible or missing
                }
            }
            if (approved == approvedBeforeBatch) {
                break;
            }
        }
        return approved;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer createSuggestion(MediaItem item, String field, String value, String providerId, float confidence) {
        return createSuggestion(item, field, value, providerId, confidence, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer createSuggestion(
            MediaItem item,
            String field,
            String value,
            String providerId,
            float confidence,
            boolean autoApproveImmediately) {
        if (item == null || item.getId() == null) {
            return null;
        }
        Integer itemId = item.getId();
        String normalizedField = field;
        String normalizedValue = value;
        MediaItem attachedItem;
        if (isTagField(field)) {
            String tagName = tagNameFrom(field, value);
            String normalizedTagName = tagCanonicalizationService.normalizeDisplayName(tagName);
            if (normalizedTagName.isBlank() || !tagQualityService.isAcceptableAiTag(normalizedTagName)) {
                return null;
            }
            // AI classification already resolves against the existing-tag map.
            // Defer database canonicalization until approval so generating a
            // suggestion never scans or mutates the tag table.
            normalizedValue = normalizedTagName;
            normalizedField = tagFieldName(normalizedValue);
            attachedItem = itemRepository.findByIdWithClassificationGraph(itemId).orElse(null);
            if (attachedItem == null || tagCanonicalizationService.itemHasEquivalentTag(attachedItem, normalizedValue)) {
                return null;
            }
            if (suggestionRepository.existsByMediaItem_IdAndFieldNameAndSuggestedValueAndReviewStatus(
                    itemId, normalizedField, normalizedValue, "PENDING")) {
                return null;
            }
        } else {
            attachedItem = itemRepository.getReferenceById(itemId);
        }
        AiSuggestion suggestion = AiSuggestion.builder()
                .mediaItem(attachedItem)
                .fieldName(normalizedField)
                .suggestedValue(normalizedValue)
                .providerId(providerId)
                .confidence(confidence)
                .reviewStatus("PENDING")
                .createdAt(Instant.now())
                .build();
        
        suggestion = suggestionRepository.save(suggestion);

        if (autoApproveImmediately && shouldAutoApprove(normalizedField, confidence)) {
            log.info("Auto-approving AI suggestion for item {} field {} with confidence {}",
                    attachedItem.getId(), normalizedField, confidence);
            try {
                approve(suggestion.getId(), AI_SYSTEM_REVIEWER_ID);
            } catch (Exception e) {
                log.error("Failed to auto-approve AI suggestion {}: {}", suggestion.getId(), e.getMessage());
            }
        }
        return suggestion.getId();
    }

    @Transactional
    public int autoApprovePendingForItems(Collection<Integer> mediaItemIds) {
        if (!isAutoApproveEnabled() || mediaItemIds == null || mediaItemIds.isEmpty()) {
            return 0;
        }
        List<Integer> ids = mediaItemIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return 0;
        }
        return autoApproveEligibleSuggestions(
                suggestionRepository.findByReviewStatusAndMediaItem_IdInOrderByIdAsc("PENDING", ids));
    }

    @Transactional
    public int autoApprovePendingSuggestions() {
        if (!isAutoApproveEnabled()) {
            return 0;
        }
        int approved = 0;
        int afterId = 0;
        while (true) {
            List<AiSuggestion> suggestions = suggestionRepository.findByReviewStatusAndIdGreaterThanOrderByIdAsc(
                    "PENDING",
                    afterId,
                    PageRequest.of(0, AUTO_APPROVE_PAGE_SIZE));
            if (suggestions.isEmpty()) {
                break;
            }
            afterId = suggestions.getLast().getId();
            approved += autoApproveEligibleSuggestions(suggestions);
        }
        return approved;
    }

    private int autoApproveEligibleSuggestions(List<AiSuggestion> suggestions) {
        int approved = 0;
        for (AiSuggestion suggestion : suggestions) {
            float confidence = suggestion.getConfidence() != null ? suggestion.getConfidence() : 0f;
            if (!shouldAutoApprove(suggestion.getFieldName(), confidence)) {
                continue;
            }
            try {
                applySuggestion(suggestion, AI_SYSTEM_REVIEWER_ID, false);
                approved++;
            } catch (Exception e) {
                log.warn("Skipping AI auto-approval for suggestion {}: {}",
                        suggestion.getId(), e.getMessage());
            }
        }
        return approved;
    }

    private boolean isAutoApproveEnabled() {
        return sysConfigService.getBoolean("ai.auto_approve.enabled", false);
    }

    private boolean shouldAutoApprove(String field, float confidence) {
        if (field == null || field.isBlank()) {
            return false;
        }
        if (!isAutoApproveEnabled()) {
            return false;
        }

        double threshold = 0.5;
        String thresholdStr = sysConfigService.getString("ai.auto_approve.confidence_threshold", "0.5");
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

    private boolean isTagField(String field) {
        return field != null && field.startsWith("tag:");
    }

    private String tagNameFrom(String field, String value) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return field != null && field.length() > 4 ? field.substring(4) : "";
    }

    private String tagFieldName(String tagName) {
        String normalized = tagCanonicalizationService.normalizeDisplayName(tagName);
        int maxNameLength = 60;
        if (normalized.length() > maxNameLength) {
            normalized = normalized.substring(0, maxNameLength).strip();
        }
        return "tag:" + normalized;
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
