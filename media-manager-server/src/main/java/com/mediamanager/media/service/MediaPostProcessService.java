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
    private final MediaChapterService mediaChapterService;

    public MediaPostProcessService(
            @Lazy ClassificationEngine classificationEngine,
            @Lazy AiOrchestrator aiOrchestrator,
            EmbeddingIndexService embeddingIndexService,
            FtsIndexService ftsIndexService,
            MediaItemRepository mediaItemRepository,
            MediaFileRepository fileRepository,
            ThumbnailService thumbnailService,
            MediaChapterService mediaChapterService) {
        this.classificationEngine = classificationEngine;
        this.aiOrchestrator = aiOrchestrator;
        this.embeddingIndexService = embeddingIndexService;
        this.ftsIndexService = ftsIndexService;
        this.mediaItemRepository = mediaItemRepository;
        this.fileRepository = fileRepository;
        this.thumbnailService = thumbnailService;
        this.mediaChapterService = mediaChapterService;
    }

    public void afterMetadataUpdated(MediaItem item) {
        Integer itemId = item != null ? item.getId() : null;
        try {
            if (itemId == null) {
                return;
            }
            mediaChapterService.ensureChaptersForItem(item);
            MediaItem processedItem = classificationEngine.executeClassification(item);
            if (processedItem.getOverview() == null || processedItem.getOverview().isBlank()) {
                aiOrchestrator.completeMetadataAsync(processedItem.getId());
            }
            processedItem = ensurePosterThumbnail(processedItem);
            syncSearchIndexes(processedItem);

        } catch (Exception e) {
            log.warn("Post-process failed for item {}: {}", itemId, e.getMessage());
        }
    }

    private MediaItem ensurePosterThumbnail(MediaItem item) {
        if (item == null || item.getId() == null || hasText(item.getPosterPath()) || !isVideoItem(item)) {
            return item;
        }
        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        if (files.isEmpty() || !hasText(files.get(0).getFilePath())) {
            return item;
        }
        String thumbnailPath = thumbnailService.generateThumbnail(item.getId(), files.get(0).getFilePath());
        if (!hasText(thumbnailPath)) {
            return item;
        }
        return mediaItemRepository.findById(item.getId())
                .map(current -> {
                    if (!hasText(current.getPosterPath())) {
                        current.setPosterPath(thumbnailPath);
                        return mediaItemRepository.save(current);
                    }
                    return current;
                })
                .orElse(item);
    }

    private static boolean isVideoItem(MediaItem item) {
        return "MOVIE".equals(item.getType()) || "TV_SHOW".equals(item.getType()) || "EPISODE".equals(item.getType());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Keeps FTS and vector indexes aligned with item visibility and metadata. */
    public void syncSearchIndexes(MediaItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        MediaItem indexedItem = mediaItemRepository.findByIdWithClassificationGraph(item.getId())
                .orElse(item);
        if (Boolean.TRUE.equals(indexedItem.getHidden())) {
            removeSearchIndexes(indexedItem.getId());
            return;
        }
        ftsIndexService.indexItem(indexedItem);
        embeddingIndexService.indexItem(indexedItem);
    }

    public void removeSearchIndexes(Integer itemId) {
        if (itemId == null) {
            return;
        }
        ftsIndexService.removeItem(itemId);
        embeddingIndexService.removeItem(itemId);
    }

    @Async("postProcessExecutor")
    public void afterMetadataUpdatedAsync(Integer itemId) {
        mediaItemRepository.findById(itemId).ifPresent(this::afterMetadataUpdated);
    }
}
