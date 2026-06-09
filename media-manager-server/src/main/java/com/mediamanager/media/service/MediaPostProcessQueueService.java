package com.mediamanager.media.service;

import com.mediamanager.media.dto.PostProcessStatsDto;
import com.mediamanager.media.entity.MediaPostProcessTask;
import com.mediamanager.media.repository.MediaPostProcessTaskRepository;
import com.mediamanager.system.dto.SystemLogEventDto;
import com.mediamanager.system.service.SystemLogBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaPostProcessQueueService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            MediaPostProcessTask.STATUS_PENDING,
            MediaPostProcessTask.STATUS_RUNNING);

    private final MediaPostProcessTaskRepository taskRepository;

    @Value("${mediamanager.post-process.max-attempts:5}")
    private int defaultMaxAttempts;

    @Value("${mediamanager.post-process.retry-backoff-seconds:60}")
    private int retryBackoffSeconds;

    @Value("${mediamanager.post-process.stale-running-minutes:30}")
    private int staleRunningMinutes;

    @Transactional
    public void enqueueItemFull(Integer itemId, String source) {
        if (itemId == null) {
            return;
        }
        if (taskRepository.existsByTaskTypeAndMediaItemIdAndStatusIn(
                MediaPostProcessTask.TYPE_ITEM_FULL, itemId, ACTIVE_STATUSES)) {
            return;
        }
        saveTask(MediaPostProcessTask.builder()
                .taskType(MediaPostProcessTask.TYPE_ITEM_FULL)
                .mediaItemId(itemId)
                .source(source)
                .maxAttempts(defaultMaxAttempts)
                .build());
    }

    @Transactional
    public void enqueueFileChapters(Integer fileId, String source) {
        if (fileId == null) {
            return;
        }
        if (taskRepository.existsByTaskTypeAndMediaFileIdAndStatusIn(
                MediaPostProcessTask.TYPE_FILE_CHAPTERS, fileId, ACTIVE_STATUSES)) {
            return;
        }
        saveTask(MediaPostProcessTask.builder()
                .taskType(MediaPostProcessTask.TYPE_FILE_CHAPTERS)
                .mediaFileId(fileId)
                .source(source)
                .maxAttempts(defaultMaxAttempts)
                .build());
    }

    private void saveTask(MediaPostProcessTask task) {
        try {
            taskRepository.save(task);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Post-process task already queued: type={}, item={}, file={}",
                    task.getTaskType(), task.getMediaItemId(), task.getMediaFileId());
        }
    }

    @Transactional
    public List<MediaPostProcessTask> claimBatch(int batchSize) {
        if (batchSize <= 0) {
            return List.of();
        }
        List<Integer> ids = taskRepository.findClaimableTaskIds(batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<MediaPostProcessTask> claimed = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            taskRepository.findById(id).ifPresent(task -> {
                task.setStatus(MediaPostProcessTask.STATUS_RUNNING);
                task.setStartedAt(now);
                task.setErrorMessage(null);
                claimed.add(taskRepository.save(task));
            });
        }
        return claimed;
    }

    @Transactional
    public void markSuccess(Integer taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(MediaPostProcessTask.STATUS_SUCCESS);
            task.setFinishedAt(Instant.now());
            task.setErrorMessage(null);
            taskRepository.save(task);
        });
    }

    @Transactional
    public void markFailed(Integer taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            int attempts = task.getAttempts() + 1;
            task.setAttempts(attempts);
            String detail = truncate(errorMessage);
            if (attempts < task.getMaxAttempts()) {
                task.setStatus(MediaPostProcessTask.STATUS_PENDING);
                task.setStartedAt(null);
                task.setNextRetryAt(Instant.now().plusSeconds((long) retryBackoffSeconds * attempts));
                task.setErrorMessage(detail);
                taskRepository.save(task);
                log.warn("Post-process task {} failed (attempt {}/{}), will retry: {}",
                        taskId, attempts, task.getMaxAttempts(), detail);
                broadcastTaskLog("WARN", "POST_PROCESS_RETRY",
                        "Task " + taskId + " retry " + attempts + "/" + task.getMaxAttempts() + ": " + detail);
                return;
            }
            task.setStatus(MediaPostProcessTask.STATUS_FAILED);
            task.setFinishedAt(Instant.now());
            task.setErrorMessage(detail);
            taskRepository.save(task);
            log.error("Post-process task {} failed permanently after {} attempts: {}",
                    taskId, attempts, detail);
            broadcastTaskLog("ERROR", "POST_PROCESS_FAILED",
                    "Task " + taskId + " failed permanently: " + detail);
        });
    }

    @Transactional(readOnly = true)
    public MediaPostProcessTask findById(Integer taskId) {
        if (taskId == null) {
            return null;
        }
        return taskRepository.findById(taskId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PostProcessStatsDto stats() {
        return PostProcessStatsDto.builder()
                .pending(taskRepository.countByStatus(MediaPostProcessTask.STATUS_PENDING))
                .running(taskRepository.countByStatus(MediaPostProcessTask.STATUS_RUNNING))
                .failed(taskRepository.countByStatus(MediaPostProcessTask.STATUS_FAILED))
                .success(taskRepository.countByStatus(MediaPostProcessTask.STATUS_SUCCESS))
                .build();
    }

    @Transactional
    public int recoverStaleRunningTasks() {
        Instant staleBefore = Instant.now().minusSeconds((long) staleRunningMinutes * 60);
        int recovered = taskRepository.resetStaleRunningTasks(staleBefore);
        if (recovered > 0) {
            log.info("Recovered {} stale post-process tasks back to PENDING", recovered);
            broadcastTaskLog("INFO", "POST_PROCESS_RECOVER",
                    "Recovered " + recovered + " stale post-process tasks");
        }
        return recovered;
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private static void broadcastTaskLog(String level, String type, String message) {
        SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                .timestamp(System.currentTimeMillis())
                .level(level)
                .source("TASK")
                .type(type)
                .message(message)
                .build());
    }
}
