package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagQualityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTagTranslationServiceTest {

    @Mock
    private AiOrchestrator aiOrchestrator;
    @Mock
    private AiProvider provider;
    @Mock
    private TagRepository tagRepository;

    private AiTagTranslationService service;

    @BeforeEach
    void setUp() {
        service = new AiTagTranslationService(
                aiOrchestrator,
                new TagCanonicalizationService(tagRepository),
                new TagQualityService(),
                new ObjectMapper());
    }

    @Test
    void asksAiOnlyForUntranslatedReusableTags() {
        List<AiOrganizationWorker.TagSnapshot> tags = List.of(
                new AiOrganizationWorker.TagSnapshot(1, "Road Trip"),
                new AiOrganizationWorker.TagSnapshot(2, "\u79d1\u5e7b"),
                new AiOrganizationWorker.TagSnapshot(3, "H.264"),
                new AiOrganizationWorker.TagSnapshot(4, "I'm sorry, here are appropriate tags"),
                new AiOrganizationWorker.TagSnapshot(5, "Action"));

        List<AiOrganizationWorker.TagSnapshot> candidates = service.aiTranslationCandidates(tags);

        assertThat(candidates)
                .extracting(AiOrganizationWorker.TagSnapshot::id)
                .containsExactly(1);
    }

    @Test
    void parsesAiJsonTranslationBatch() {
        List<AiOrganizationWorker.TagSnapshot> batch = List.of(
                new AiOrganizationWorker.TagSnapshot(1, "Road Trip"));
        when(aiOrchestrator.resolve(7, AiTaskType.COMPLETE_METADATA)).thenReturn(provider);
        when(aiOrchestrator.defaultConfig(7, AiTaskType.COMPLETE_METADATA)).thenReturn(Map.of());
        when(provider.providerId()).thenReturn("openai-compatible");
        when(provider.completeMetadata(anyString(), anyMap()))
                .thenReturn(Optional.of("[{\"id\":1,\"zh\":\"\u516c\u8def\u65c5\u884c\"}]"));

        Map<Integer, String> translations = service.translateBatch(7, batch);

        assertThat(translations).containsEntry(1, "\u516c\u8def\u65c5\u884c");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).completeMetadata(prompt.capture(), anyMap());
        assertThat(prompt.getValue()).contains("Road Trip");
    }
}
