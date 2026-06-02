package com.mediamanager.classification.service.strategy;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.AiSuggestionService;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.media.entity.MediaItem;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(300)
public class AiClassifier implements com.mediamanager.classification.spi.ClassifierStrategy {

    private final AiOrchestrator aiOrchestrator;
    private final AiSuggestionService aiSuggestionService;

    public AiClassifier(@Lazy AiOrchestrator aiOrchestrator, @Lazy AiSuggestionService aiSuggestionService) {
        this.aiOrchestrator = aiOrchestrator;
        this.aiSuggestionService = aiSuggestionService;
    }

    @Override
    public void classify(MediaItem item) {
        if (!aiOrchestrator.isClassifierEnabled()) {
            return;
        }
        String title = item.getTitle();
        if (title == null || title.isBlank()) {
            return;
        }
        Integer libraryId = item.getLibrary() != null ? item.getLibrary().getId() : null;
        AiProvider provider = aiOrchestrator.resolve(libraryId, AiTaskType.SUGGEST_TAGS);
        if ("noop".equals(provider.providerId())) {
            return;
        }
        String overview = item.getOverview() != null ? item.getOverview().trim() : "";
        String prompt = overview.isBlank()
                ? "Suggest comma-separated tags for: " + title
                : "Suggest comma-separated tags for: " + title + " — " + overview;
        List<String> tags = provider.suggestTags(prompt, aiOrchestrator.defaultConfig(libraryId));
        for (String tag : tags) {
            aiSuggestionService.createSuggestion(item, "tag:" + tag, tag, provider.providerId(), 0.75f);
        }
    }

    @Override
    public int getPriority() {
        return 300;
    }
}
