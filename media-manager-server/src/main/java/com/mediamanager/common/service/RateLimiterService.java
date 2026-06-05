package com.mediamanager.common.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Thread-safe rate limiting service using Semaphore pattern.
 * Restricts concurrent execution on external integrations.
 */
@Service
public class RateLimiterService {

    private final Map<String, Semaphore> limiters = new ConcurrentHashMap<>();

    /**
     * Executes a task within a rate limit / concurrency limit context.
     *
     * @param key           rate limit category key (e.g., "tmdb")
     * @param maxConcurrent maximum concurrent requests allowed
     * @param task          callable task to execute
     * @param <T>           response type
     * @return result of the task
     */
    public <T> T executeWithRateLimit(String key, int maxConcurrent, Callable<T> task) {
        Semaphore semaphore = limiters.computeIfAbsent(key, k -> new Semaphore(maxConcurrent));
        try {
            semaphore.acquire();
            return task.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task execution interrupted while waiting for rate limit semaphore", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }
}
