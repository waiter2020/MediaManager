package com.mediamanager.media.service;

import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.EmbeddingIndexService;
import com.mediamanager.classification.service.ClassificationEngine;
import com.mediamanager.search.service.FtsIndexService;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MediaPostProcessService {

    private final ClassificationEngine classificationEngine;
    private final AiOrchestrator aiOrchestrator;
    private final EmbeddingIndexService embeddingIndexService;
    private final FtsIndexService ftsIndexService;
    private final MediaItemRepository mediaItemRepository;
    private final MediaFileRepository fileRepository;
    private final ThumbnailService thumbnailService;

    public MediaPostProcessService(
            @Lazy ClassificationEngine classificationEngine,
            @Lazy AiOrchestrator aiOrchestrator,
            EmbeddingIndexService embeddingIndexService,
            FtsIndexService ftsIndexService,
            MediaItemRepository mediaItemRepository,
            MediaFileRepository fileRepository,
            ThumbnailService thumbnailService) {
        this.classificationEngine = classificationEngine;
        this.aiOrchestrator = aiOrchestrator;
        this.embeddingIndexService = embeddingIndexService;
        this.ftsIndexService = ftsIndexService;
        this.mediaItemRepository = mediaItemRepository;
        this.fileRepository = fileRepository;
        this.thumbnailService = thumbnailService;
    }

    public void afterMetadataUpdated(MediaItem item) {
        try {
            classificationEngine.executeClassification(item);
            if (item.getOverview() == null || item.getOverview().isBlank()) {
                aiOrchestrator.completeMetadataAsync(item.getId());
            }
            syncSearchIndexes(item);

            // Asynchronously generate dynamic video previews (animated WebP)
            if ("MOVIE".equals(item.getType()) || "TV_SHOW".equals(item.getType())) {
                List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
                if (!files.isEmpty()) {
                    MediaFile primaryFile = files.get(0);
                    thumbnailService.generatePreviewWebp(item.getId(), primaryFile.getFilePath());
                }
            }
        } catch (Exception e) {
            log.warn("Post-process failed for item {}: {}", item.getId(), e.getMessage());
        }
    }

    /** Keeps FTS and vector indexes aligned with item visibility and metadata. */
    public void syncSearchIndexes(MediaItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(item.getHidden())) {
            removeSearchIndexes(item.getId());
            return;
        }
        ftsIndexService.indexItem(item);
        embeddingIndexService.indexItem(item);
    }

    public void removeSearchIndexes(Integer itemId) {
        if (itemId == null) {
            return;
        }
        ftsIndexService.removeItem(itemId);
        embeddingIndexService.removeItem(itemId);
    }

    @Async
    public void afterMetadataUpdatedAsync(Integer itemId) {
        mediaItemRepository.findById(itemId).ifPresent(this::afterMetadataUpdated);
    }
}
