package com.mediamanager.sync.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;

import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class EventDebouncer {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> delayedTasks = new ConcurrentHashMap<>();

    /**
     * Debounce an action using a key.
     * If called again with the same key before the delay expires, the previous action is cancelled
     * and a new one is scheduled.
     * 
     * @param key Unique key for the debounce stream (e.g. "path/to/folder")
     * @param action The runnable to execute
     * @param delayMs Delay in milliseconds
     */
    public void debounce(String key, Runnable action, long delayMs) {
        ScheduledFuture<?> existingTask = delayedTasks.get(key);
        if (existingTask != null && !existingTask.isDone()) {
            boolean cancelled = existingTask.cancel(false);
            if (cancelled) {
                log.trace("Cancelled debounced task for key: {}", key);
            }
        }

        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            try {
                action.run();
            } finally {
                delayedTasks.remove(key);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        delayedTasks.put(key, newTask);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down EventDebouncer scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

