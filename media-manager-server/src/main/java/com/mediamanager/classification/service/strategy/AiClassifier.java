package com.mediamanager.classification.service.strategy;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.AiSuggestionService;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.media.entity.MediaItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(300)
public class AiClassifier implements com.mediamanager.classification.spi.ClassifierStrategy {

    private final AiOrchestrator aiOrchestrator;
    private final AiSuggestionService aiSuggestionService;

    @Value("${mediamanager.ai.classifier.enabled:true}")
    private boolean enabled;

    public AiClassifier(AiOrchestrator aiOrchestrator, AiSuggestionService aiSuggestionService) {
        this.aiOrchestrator = aiOrchestrator;
        this.aiSuggestionService = aiSuggestionService;
    }

    @Override
    public void classify(MediaItem item) {
        if (!enabled || item.getOverview() == null || item.getOverview().isBlank()) {
            return;
        }
        AiProvider provider = aiOrchestrator.resolve(AiTaskType.SUGGEST_TAGS);
        String prompt = "Suggest comma-separated tags for: " + item.getTitle() + " — " + item.getOverview();
        List<String> tags = provider.suggestTags(prompt, aiOrchestrator.defaultConfig());
        for (String tag : tags) {
            aiSuggestionService.createSuggestion(item, "tag:" + tag, tag, provider.providerId(), 0.75f);
        }
    }

    @Override
    public int getPriority() {
        return 300;
    }
}
