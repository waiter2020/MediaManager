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

    @Value("${mediamanager.ffprobe.path:ffprobe}")
    private String yamlFfprobePath;

    public boolean isFfmpegAvailable() {
        return isCommandAvailable(sysConfigService.ffmpegPath(yamlFfmpegPath));
    }

    public boolean isFfprobeAvailable() {
        return isCommandAvailable(sysConfigService.ffprobePath(yamlFfprobePath));
    }

    private boolean isCommandAvailable(String path) {
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
        caps.put("ffprobeAvailable", isFfprobeAvailable());
        caps.put("ffprobePath", sysConfigService.ffprobePath(yamlFfprobePath));
        caps.put("embeddingCount", embeddingIndexService.countEmbeddings());
        caps.put("hasIndexedVectors", embeddingIndexService.hasIndexedVectors());
        boolean embeddingAvailable = aiOrchestrator.isEmbeddingAvailable();
        caps.put("embeddingAvailable", embeddingAvailable);

        AiProvider embedProvider = aiOrchestrator.resolve(AiTaskType.EMBED_TEXT);
        AiProvider llmProvider = aiOrchestrator.resolve(AiTaskType.NL_QUERY);
        var embedConfig = aiOrchestrator.defaultConfig(AiTaskType.EMBED_TEXT);
        var llmConfig = aiOrchestrator.defaultConfig(AiTaskType.NL_QUERY);
        String providerId = embedProvider.providerId();
        caps.put("aiProvider", providerId);
        caps.put("aiProviderName", embedProvider.displayName());
        caps.put("embedProvider", providerId);
        caps.put("embedProviderName", embedProvider.displayName());
        caps.put("llmProvider", llmProvider.providerId());
        caps.put("llmProviderName", llmProvider.displayName());
        caps.put("embedModel", embedConfig.get("embedModel"));
        caps.put("llmModel", llmConfig.get("llmModel"));
        caps.put("aiBaseUrl", embedConfig.get("baseUrl"));
        caps.put("embedBaseUrl", embedConfig.get("baseUrl"));
        caps.put("llmBaseUrl", llmConfig.get("baseUrl"));
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
