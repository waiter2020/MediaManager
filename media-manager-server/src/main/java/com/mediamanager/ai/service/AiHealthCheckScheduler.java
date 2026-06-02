package com.mediamanager.ai.service;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class AiHealthCheckScheduler implements ApplicationListener<ContextRefreshedEvent> {

    private final AiOrchestrator aiOrchestrator;
    private final Map<String, Object> healthCache = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public AiHealthCheckScheduler(@Lazy AiOrchestrator aiOrchestrator) {
        this.aiOrchestrator = aiOrchestrator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Asynchronously populate cache on startup once context is fully refreshed to avoid deadlocks
        log.info("Spring context refreshed, asynchronously running initial AI health check...");
        Thread.ofVirtual().name("ai-health-init").start(this::refreshHealthCache);
    }

    @Scheduled(fixedRate = 300000) // 5 minutes
    public void scheduleHealthCheck() {
        log.debug("Starting scheduled AI health check...");
        refreshHealthCache();
    }

    public void refreshHealthCache() {
        refreshHealthCache(false);
    }

    public Map<String, Object> refreshHealthCacheNow() {
        refreshHealthCache(true);
        return getCachedHealth();
    }

    private void refreshHealthCache(boolean waitForLock) {
        if (waitForLock) {
            lock.lock();
        } else if (!lock.tryLock()) {
            log.debug("AI health check is already in progress, skipping concurrent execution.");
            return;
        }
        try {
            AiProvider provider = aiOrchestrator.resolve(AiTaskType.EMBED_TEXT);
            var config = aiOrchestrator.defaultConfig();
            boolean noop = "noop".equalsIgnoreCase(provider.providerId());
            
            long start = System.currentTimeMillis();
            boolean ok = false;
            int embeddingDimensions = 0;
            if (!noop) {
                try {
                    // Try to perform a lightweight embedding request
                    float[] embed = aiOrchestrator.embedText("ping");
                    embeddingDimensions = embed != null ? embed.length : 0;
                    ok = embeddingDimensions > 0;
                } catch (Exception e) {
                    log.warn("Asynchronous AI health check embed call failed: {}", e.getMessage());
                }
            }
            long latency = System.currentTimeMillis() - start;

            String message;
            if (noop) {
                message = "AI 提供方未就绪（noop），请在系统设置或库级插件中配置 Ollama/OpenAI 兼容服务";
            } else if (ok) {
                message = "嵌入服务可用 (延迟 " + latency + "ms)";
            } else {
                message = "嵌入服务不可用，请确认 Ollama 已启动且已拉取 embed 模型";
            }

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("provider", provider.providerId());
            snapshot.put("displayName", provider.displayName());
            snapshot.put("noop", noop);
            snapshot.put("embedModel", config.getOrDefault("embedModel", "nomic-embed-text"));
            snapshot.put("llmModel", config.getOrDefault("llmModel", "qwen2.5:7b"));
            snapshot.put("baseUrl", config.getOrDefault("baseUrl", ""));
            snapshot.put("classifierEnabled", aiOrchestrator.isClassifierEnabled());
            snapshot.put("status", ok ? "ok" : "degraded");
            snapshot.put("message", message);
            snapshot.put("embeddingDimensions", embeddingDimensions);
            snapshot.put("latencyMs", noop ? 0L : latency);
            snapshot.put("checkedAt", Instant.now().toString());
            healthCache.clear();
            healthCache.putAll(snapshot);

            log.info("AI health check completed: status={}, provider={}, latency={}ms", 
                     ok ? "ok" : "degraded", provider.providerId(), noop ? 0L : latency);
        } catch (Exception e) {
            log.error("AI health check scheduler ran into an error", e);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("status", "degraded");
            snapshot.put("message", "AI 健康检查失败：" + e.getMessage());
            snapshot.put("noop", true);
            snapshot.put("classifierEnabled", false);
            snapshot.put("embeddingDimensions", 0);
            snapshot.put("checkedAt", Instant.now().toString());
            healthCache.clear();
            healthCache.putAll(snapshot);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> getCachedHealth() {
        if (healthCache.isEmpty()) {
            return Map.of(
                "status", "unknown",
                "message", "AI 状态正在后台加载中...",
                "noop", true,
                "classifierEnabled", false,
                "embeddingDimensions", 0
            );
        }
        return Map.copyOf(healthCache);
    }
}
