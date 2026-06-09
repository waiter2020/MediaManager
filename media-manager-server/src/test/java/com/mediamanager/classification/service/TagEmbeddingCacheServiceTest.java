package com.mediamanager.classification.service;

import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.service.AiOrchestrator;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagEmbeddingCacheServiceTest {

    @Mock
    private AiOrchestrator aiOrchestrator;
    @Mock
    private AiProvider provider;

    private TagEmbeddingCacheService service;

    @BeforeEach
    void setUp() {
        TagCanonicalizationService canonicalizationService = new TagCanonicalizationService(
                mock(TagRepository.class),
                mock(TagSimilarityService.class),
                mock(TagEmbeddingClusterService.class));
        service = new TagEmbeddingCacheService(aiOrchestrator, canonicalizationService);
        when(aiOrchestrator.resolve(1, AiTaskType.EMBED_TEXT)).thenReturn(provider);
        when(provider.providerId()).thenReturn("openai-compatible");
        when(aiOrchestrator.embedModelId(1)).thenReturn("openai-compatible:text-embedding-v4");
        when(aiOrchestrator.embedTexts(any(), eq(1))).thenReturn(List.of(
                new float[] {1f, 0f},
                new float[] {0f, 1f}));
    }

    @Test
    void embedAllUsesBatchRequestAndCachesResults() {
        Map<String, float[]> first = service.embedAll(List.of("\u9a91\u4e58", "\u9a91\u4e58\u4f4d"), 1);
        Map<String, float[]> second = service.embedAll(List.of("\u9a91\u4e58", "\u9a91\u4e58\u4f4d"), 1);

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        verify(aiOrchestrator, times(1)).embedTexts(any(), eq(1));
    }
}
