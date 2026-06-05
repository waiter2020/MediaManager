package com.mediamanager.classification.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.service.AiSuggestionService;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.media.entity.MediaItem;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiClassifierTest {

    @Mock
    private AiOrchestrator aiOrchestrator;
    @Mock
    private AiSuggestionService aiSuggestionService;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private TagCanonicalizationService tagCanonicalizationService;
    @Mock
    private AiProvider provider;

    private AiClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AiClassifier(
                aiOrchestrator,
                aiSuggestionService,
                tagRepository,
                tagCanonicalizationService,
                new TagQualityService(),
                new ObjectMapper());

        when(aiOrchestrator.isClassifierEnabled()).thenReturn(true);
        when(aiOrchestrator.resolve(null, AiTaskType.SUGGEST_TAGS)).thenReturn(provider);
        when(aiOrchestrator.defaultConfig(null, AiTaskType.SUGGEST_TAGS)).thenReturn(Map.of());
        when(provider.providerId()).thenReturn("test-ai");
        when(tagCanonicalizationService.normalizeDisplayName(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tagCanonicalizationService.semanticKey(anyString()))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).toLowerCase());
    }

    @Test
    void prefersExistingTagsButAllowsUsefulNewTagsByDefault() {
        TagRepository.TagUsageProjection existingTag = mock(TagRepository.TagUsageProjection.class);
        when(existingTag.getTagName()).thenReturn("科幻");
        when(tagRepository.findGlobalUsageCounts()).thenReturn(List.of(existingTag));
        when(provider.completeMetadata(anyString(), anyMap()))
                .thenReturn(Optional.of("[{\"id\":1,\"tags\":[\"科幻\",\"太空探索\"]}]"));

        MediaItem item = MediaItem.builder().id(1).title("Space Voyage").type("MOVIE").build();
        MediaItem image = MediaItem.builder().id(2).title("Cover Art").type("IMAGE").build();
        classifier.classifyBatch(List.of(item, image));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).completeMetadata(prompt.capture(), anyMap());
        assertThat(prompt.getValue())
                .contains("\"allowNewTags\":true")
                .contains("Prefer an exact value from existingTags")
                .contains("Add a new tag when existingTags do not cover")
                .contains("Space Voyage")
                .doesNotContain("Cover Art");

        verify(aiSuggestionService).createSuggestion(
                eq(item), eq("tag:科幻"), eq("科幻"), eq("test-ai"), anyFloat());
        verify(aiSuggestionService).createSuggestion(
                eq(item), eq("tag:太空探索"), eq("太空探索"), eq("test-ai"), anyFloat());
        verify(aiSuggestionService, never()).createSuggestion(
                eq(image), anyString(), anyString(), anyString(), anyFloat());
        verify(tagCanonicalizationService, never()).resolveCanonicalName(anyString());
    }
}
