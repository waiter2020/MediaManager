package com.mediamanager.media.service;

import com.mediamanager.ai.service.EmbeddingIndexService;
import com.mediamanager.classification.service.ClassificationEngine;
import com.mediamanager.search.service.FtsIndexService;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaPostProcessService {

    private final ClassificationEngine classificationEngine;
    private final EmbeddingIndexService embeddingIndexService;
    private final FtsIndexService ftsIndexService;
    private final MediaItemRepository mediaItemRepository;

    public void afterMetadataUpdated(MediaItem item) {
        try {
            classificationEngine.executeClassification(item);
            ftsIndexService.indexItem(item);
            embeddingIndexService.indexItem(item);
        } catch (Exception e) {
            log.warn("Post-process failed for item {}: {}", item.getId(), e.getMessage());
        }
    }

    @Async
    public void afterMetadataUpdatedAsync(Integer itemId) {
        mediaItemRepository.findById(itemId).ifPresent(this::afterMetadataUpdated);
    }
}
