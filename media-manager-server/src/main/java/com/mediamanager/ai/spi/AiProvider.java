package com.mediamanager.ai.spi;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.plugin.MediaManagerPlugin;
import com.mediamanager.plugin.PluginKind;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AiProvider extends MediaManagerPlugin {

    @Override
    default String id() {
        return providerId();
    }

    @Override
    default PluginKind kind() {
        return PluginKind.AI_PROVIDER;
    }

    String providerId();

    boolean supports(AiTaskType taskType);

    float[] embedText(String text, Map<String, Object> config);

    default List<float[]> embedTexts(List<String> texts, Map<String, Object> config) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return texts.stream().map(text -> embedText(text, config)).toList();
    }

    Optional<String> completeMetadata(String prompt, Map<String, Object> config);

    List<String> suggestTags(String prompt, Map<String, Object> config);

    Optional<Map<String, Object>> parseNaturalLanguage(String query, Map<String, Object> config);
}
