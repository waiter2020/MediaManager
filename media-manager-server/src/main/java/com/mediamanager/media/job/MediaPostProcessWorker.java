package com.mediamanager.media.job;

import com.mediamanager.media.entity.MediaPostProcessTask;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.MediaChapterService;
import com.mediamanager.media.service.MediaPostProcessQueueService;
import com.mediamanager.media.service.MediaPostProcessService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class MediaPostProcessWorker {

    private final MediaPostProcessQueueService queueService;
    private final MediaPostProcessService mediaPostProcessService;
    private final MediaChapterService mediaChapterService;
    private final MediaItemRepository mediaItemRepository;
    private final Executor postProcessExecutor;

    @Value("${mediamanager.post-process.batch-size:10}")
    private int batchSize;

    public MediaPostProcessWorker(
            MediaPostProcessQueueService queueService,
            MediaPostProcessService mediaPostProcessService,
            MediaChapterService mediaChapterService,
            MediaItemRepository mediaItemRepository,
            @Qualifier("postProcessExecutor") Executor postProcessExecutor) {
        this.queueService = queueService;
        this.mediaPostProcessService = mediaPostProcessService;
        this.mediaChapterService = mediaChapterService;
        this.mediaItemRepository = mediaItemRepository;
        this.postProcessExecutor = postProcessExecutor;
    }

    @PostConstruct
    public void recoverOnStartup() {
        queueService.recoverStaleRunningTasks();
    }

    @Scheduled(fixedDelayString = "${mediamanager.post-process.worker-interval-ms:2000}")
    public void pollAndDispatch() {
        List<MediaPostProcessTask> claimed = queueService.claimBatch(batchSize);
        for (MediaPostProcessTask task : claimed) {
            Integer taskId = task.getId();
            postProcessExecutor.execute(() -> executeTask(taskId));
        }
    }

    private void executeTask(Integer taskId) {
        try {
            MediaPostProcessTask task = queueService.findById(taskId);
            if (task == null) {
                return;
            }
            switch (task.getTaskType()) {
                case MediaPostProcessTask.TYPE_ITEM_FULL -> mediaItemRepository.findById(task.getMediaItemId())
                        .ifPresent(mediaPostProcessService::afterMetadataUpdated);
                case MediaPostProcessTask.TYPE_FILE_CHAPTERS ->
                        mediaChapterService.processChaptersForFile(task.getMediaFileId());
                default -> throw new IllegalStateException("Unknown post-process task type: " + task.getTaskType());
            }
            queueService.markSuccess(taskId);
        } catch (Exception e) {
            log.warn("Post-process worker task {} failed: {}", taskId, e.getMessage());
            queueService.markFailed(taskId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
