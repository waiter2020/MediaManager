package com.mediamanager.system.service;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.EmbeddingIndexService;
import com.mediamanager.ai.spi.AiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SystemCapabilitiesService {

    private final SysConfigService sysConfigService;
    private final AiOrchestrator aiOrchestrator;
    private final EmbeddingIndexService embeddingIndexService;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String yamlFfmpegPath;

    public boolean isFfmpegAvailable() {
        String path = sysConfigService.ffmpegPath(yamlFfmpegPath);
        try {
            Process process = new ProcessBuilder(path, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> capabilitiesSnapshot() {
        Map<String, Object> caps = new HashMap<>();
        caps.put("ffmpegAvailable", isFfmpegAvailable());
        caps.put("ffmpegPath", sysConfigService.ffmpegPath(yamlFfmpegPath));
        caps.put("embeddingCount", embeddingIndexService.countEmbeddings());
        caps.put("hasIndexedVectors", embeddingIndexService.hasIndexedVectors());
        boolean embeddingAvailable = aiOrchestrator.isEmbeddingAvailable();
        caps.put("embeddingAvailable", embeddingAvailable);

        AiProvider provider = aiOrchestrator.resolve(AiTaskType.EMBED_TEXT);
        var config = aiOrchestrator.defaultConfig();
        String providerId = provider.providerId();
        caps.put("aiProvider", providerId);
        caps.put("aiProviderName", provider.displayName());
        caps.put("embedModel", config.get("embedModel"));
        caps.put("llmModel", config.get("llmModel"));
        caps.put("aiBaseUrl", config.get("baseUrl"));
        caps.put("classifierEnabled", aiOrchestrator.isClassifierEnabled());
        caps.put("isNoopProvider", "noop".equalsIgnoreCase(providerId));
        caps.put("aiDegraded", "noop".equalsIgnoreCase(providerId) || !embeddingAvailable);
        if ("noop".equalsIgnoreCase(providerId)) {
            caps.put("aiDegradedReason", "AI 提供方未配置或不可用，已降级为 noop");
        } else if (!embeddingAvailable) {
            caps.put("aiDegradedReason", "嵌入服务不可达，语义搜索与相似推荐将受限");
        }
        return caps;
    }
}
