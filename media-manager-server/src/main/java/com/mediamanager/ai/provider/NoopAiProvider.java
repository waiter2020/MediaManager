package com.mediamanager.ai.provider;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NoopAiProvider implements AiProvider {

    @Override
    public String providerId() {
        return "noop";
    }

    @Override
    public String displayName() {
        return "No-op AI";
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return true;
    }

    @Override
    public float[] embedText(String text, Map<String, Object> config) {
        return new float[0];
    }

    @Override
    public Optional<String> completeMetadata(String prompt, Map<String, Object> config) {
        return Optional.empty();
    }

    @Override
    public List<String> suggestTags(String prompt, Map<String, Object> config) {
        return Collections.emptyList();
    }

    @Override
    public Optional<Map<String, Object>> parseNaturalLanguage(String query, Map<String, Object> config) {
        return Optional.empty();
    }
}
