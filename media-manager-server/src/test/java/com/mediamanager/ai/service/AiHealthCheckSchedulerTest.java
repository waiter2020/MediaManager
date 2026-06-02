package com.mediamanager.ai.service;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiHealthCheckSchedulerTest {

    @Mock
    private AiOrchestrator aiOrchestrator;

    @Mock
    private AiProvider aiProvider;

    private AiHealthCheckScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AiHealthCheckScheduler(aiOrchestrator);
    }

    @Test
    void getCachedHealthReturnsEmptyUnknownStateInitially() {
        Map<String, Object> initialHealth = scheduler.getCachedHealth();
        assertNotNull(initialHealth);
        assertEquals("unknown", initialHealth.get("status"));
        assertEquals("AI 状态正在后台加载中...", initialHealth.get("message"));
        assertTrue((Boolean) initialHealth.get("noop"));
        assertFalse((Boolean) initialHealth.get("classifierEnabled"));
        assertEquals(0, initialHealth.get("embeddingDimensions"));
    }

    @Test
    void refreshHealthCacheWithNoopProvider() {
        when(aiOrchestrator.resolve(AiTaskType.EMBED_TEXT)).thenReturn(aiProvider);
        when(aiProvider.providerId()).thenReturn("noop");
        when(aiProvider.displayName()).thenReturn("No-op AI Provider");
        
        Map<String, Object> config = new HashMap<>();
        config.put("embedModel", "nomic-embed-text");
        config.put("llmModel", "qwen2.5:7b");
        config.put("baseUrl", "");
        when(aiOrchestrator.defaultConfig()).thenReturn(config);
        when(aiOrchestrator.isClassifierEnabled()).thenReturn(false);

        scheduler.refreshHealthCache();

        Map<String, Object> cachedHealth = scheduler.getCachedHealth();
        assertNotNull(cachedHealth);
        assertEquals("noop", cachedHealth.get("provider"));
        assertEquals("No-op AI Provider", cachedHealth.get("displayName"));
        assertTrue((Boolean) cachedHealth.get("noop"));
        assertEquals("degraded", cachedHealth.get("status"));
        assertEquals(0L, cachedHealth.get("latencyMs"));
        assertEquals(0, cachedHealth.get("embeddingDimensions"));
        assertTrue(cachedHealth.get("message").toString().contains("noop"));
    }

    @Test
    void refreshHealthCacheWithHealthyProvider() {
        when(aiOrchestrator.resolve(AiTaskType.EMBED_TEXT)).thenReturn(aiProvider);
        when(aiProvider.providerId()).thenReturn("ollama");
        when(aiProvider.displayName()).thenReturn("Ollama AI Provider");
        
        Map<String, Object> config = new HashMap<>();
        config.put("embedModel", "nomic-embed-text");
        config.put("llmModel", "qwen2.5:7b");
        config.put("baseUrl", "http://localhost:11434");
        when(aiOrchestrator.defaultConfig()).thenReturn(config);
        when(aiOrchestrator.isClassifierEnabled()).thenReturn(true);
        
        // Mock successful embedding check
        float[] fakeEmbed = new float[]{0.1f, 0.2f, 0.3f};
        when(aiOrchestrator.embedText("ping")).thenReturn(fakeEmbed);

        scheduler.refreshHealthCache();

        Map<String, Object> cachedHealth = scheduler.getCachedHealth();
        assertNotNull(cachedHealth);
        assertEquals("ollama", cachedHealth.get("provider"));
        assertEquals("Ollama AI Provider", cachedHealth.get("displayName"));
        assertFalse((Boolean) cachedHealth.get("noop"));
        assertEquals("ok", cachedHealth.get("status"));
        assertTrue((Long) cachedHealth.get("latencyMs") >= 0L);
        assertEquals(3, cachedHealth.get("embeddingDimensions"));
        assertTrue(cachedHealth.get("message").toString().contains("可用"));
    }

    @Test
    void refreshHealthCacheWithDegradedProviderOnException() {
        when(aiOrchestrator.resolve(AiTaskType.EMBED_TEXT)).thenReturn(aiProvider);
        when(aiProvider.providerId()).thenReturn("ollama");
        when(aiProvider.displayName()).thenReturn("Ollama AI Provider");
        
        Map<String, Object> config = new HashMap<>();
        config.put("embedModel", "nomic-embed-text");
        config.put("llmModel", "qwen2.5:7b");
        config.put("baseUrl", "http://localhost:11434");
        when(aiOrchestrator.defaultConfig()).thenReturn(config);
        when(aiOrchestrator.isClassifierEnabled()).thenReturn(true);
        
        // Mock failing embedding check
        when(aiOrchestrator.embedText("ping")).thenThrow(new RuntimeException("Connection refused"));

        scheduler.refreshHealthCache();

        Map<String, Object> cachedHealth = scheduler.getCachedHealth();
        assertNotNull(cachedHealth);
        assertEquals("degraded", cachedHealth.get("status"));
        assertEquals(0, cachedHealth.get("embeddingDimensions"));
        assertTrue(cachedHealth.get("message").toString().contains("不可用"));
    }

    @Test
    void applicationEventTriggersInitialHealthCheck() throws InterruptedException {
        // Since onApplicationEvent uses virtual threads to asynchronously run, we mock it.
        // We verify that resolve is eventually called or mock it to ensure it executes.
        when(aiOrchestrator.resolve(AiTaskType.EMBED_TEXT)).thenReturn(aiProvider);
        when(aiProvider.providerId()).thenReturn("noop");
        when(aiProvider.displayName()).thenReturn("No-op");
        when(aiOrchestrator.defaultConfig()).thenReturn(new HashMap<>());
        
        ContextRefreshedEvent event = mock(ContextRefreshedEvent.class);
        scheduler.onApplicationEvent(event);
        
        // Sleep briefly to let the virtual thread finish
        int retries = 10;
        while (retries > 0 && scheduler.getCachedHealth().get("status").equals("unknown")) {
            Thread.sleep(50);
            retries--;
        }
        
        Map<String, Object> cachedHealth = scheduler.getCachedHealth();
        assertNotEquals("unknown", cachedHealth.get("status"));
    }
}
