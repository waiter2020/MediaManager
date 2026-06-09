package com.mediamanager.media.service;

import com.mediamanager.integration.IntegrationTestSupport;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.entity.MediaPostProcessTask;
import com.mediamanager.media.repository.MediaPostProcessTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MediaPostProcessQueueServiceTest extends IntegrationTestSupport {

    @Autowired
    private MediaPostProcessQueueService queueService;

    @Autowired
    private MediaPostProcessTaskRepository taskRepository;

    @BeforeEach
    void cleanTasks() {
        taskRepository.deleteAll();
    }

    @Test
    void enqueueItemFullDeduplicatesActiveTasks() {
        MediaLibrary library = createLibrary("Queue Library");
        MediaItem item = createItem(library, "Queued Item");

        queueService.enqueueItemFull(item.getId(), "SCAN");
        queueService.enqueueItemFull(item.getId(), "SCAN");

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(taskRepository.findAll().get(0).getTaskType()).isEqualTo(MediaPostProcessTask.TYPE_ITEM_FULL);
        assertThat(taskRepository.findAll().get(0).getStatus()).isEqualTo(MediaPostProcessTask.STATUS_PENDING);
    }

    @Test
    void claimBatchMarksTasksRunning() {
        MediaLibrary library = createLibrary("Claim Library");
        MediaItem item = createItem(library, "Claim Item");
        queueService.enqueueItemFull(item.getId(), "MANUAL");

        var claimed = queueService.claimBatch(5);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getStatus()).isEqualTo(MediaPostProcessTask.STATUS_RUNNING);
        assertThat(claimed.get(0).getStartedAt()).isNotNull();
    }

    @Test
    void markFailedSchedulesRetryBeforeMaxAttempts() {
        MediaLibrary library = createLibrary("Retry Library");
        MediaItem item = createItem(library, "Retry Item");
        queueService.enqueueItemFull(item.getId(), "MANUAL");
        Integer taskId = queueService.claimBatch(1).get(0).getId();

        queueService.markFailed(taskId, "temporary failure");

        MediaPostProcessTask task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(MediaPostProcessTask.STATUS_PENDING);
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getNextRetryAt()).isAfter(Instant.now());
    }

    @Test
    void recoverStaleRunningTasksResetsToPending() {
        MediaLibrary library = createLibrary("Recover Library");
        MediaItem item = createItem(library, "Recover Item");
        MediaFile file = createFile(item, "/tmp/recover.mkv");

        MediaPostProcessTask stale = MediaPostProcessTask.builder()
                .taskType(MediaPostProcessTask.TYPE_FILE_CHAPTERS)
                .mediaFileId(file.getId())
                .status(MediaPostProcessTask.STATUS_RUNNING)
                .startedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .maxAttempts(5)
                .build();
        taskRepository.save(stale);

        int recovered = queueService.recoverStaleRunningTasks();

        assertThat(recovered).isEqualTo(1);
        MediaPostProcessTask updated = taskRepository.findById(stale.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MediaPostProcessTask.STATUS_PENDING);
        assertThat(updated.getStartedAt()).isNull();
    }
}
