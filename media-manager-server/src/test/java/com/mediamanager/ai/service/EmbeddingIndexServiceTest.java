package com.mediamanager.ai.service;

import com.mediamanager.ai.entity.MediaEmbedding;
import com.mediamanager.ai.repository.MediaEmbeddingRepository;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.library.entity.MediaLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingIndexServiceTest {

    @Mock
    private MediaEmbeddingRepository embeddingRepository;
    @Mock
    private MediaItemRepository itemRepository;
    @Mock
    private AiOrchestrator aiOrchestrator;

    @InjectMocks
    private EmbeddingIndexService embeddingIndexService;

    @Test
    void rebuildAllUsesBulkDeleteWithoutLoadingBlobs() {
        MediaItem item = MediaItem.builder().id(1).title("Test").hidden(false).build();
        when(itemRepository.findAll()).thenReturn(List.of(item));
        when(aiOrchestrator.embedText(anyString(), any())).thenReturn(new float[] {1f, 0f});

        int count = embeddingIndexService.rebuildAll();

        assertThat(count).isEqualTo(1);
        verify(embeddingRepository).deleteAllInBulk();
        verify(embeddingRepository, never()).deleteAll();
    }

    @Test
    void emptyLibraryFilterReturnsNoResults() {
        float[] query = {1f, 0f};
        when(aiOrchestrator.embedText(anyString())).thenReturn(query);

        List<EmbeddingIndexService.Scored> results =
                embeddingIndexService.searchSimilarWithScores("test", Collections.emptySet(), 10);

        assertThat(results).isEmpty();
    }

    @Test
    void searchSimilarToItemExcludesSourceAndRespectsLibrary() {
        float[] vec = {1f, 0f};
        MediaEmbedding sourceEmb = MediaEmbedding.builder()
                .mediaItemId(1)
                .modelId("default")
                .vector(toBytes(vec))
                .updatedAt(Instant.now())
                .build();
        MediaEmbedding otherEmb = MediaEmbedding.builder()
                .mediaItemId(2)
                .modelId("default")
                .vector(toBytes(vec))
                .updatedAt(Instant.now())
                .build();
        when(aiOrchestrator.defaultConfig()).thenReturn(java.util.Map.of("embedModel", "default"));
        when(embeddingRepository.findByMediaItemIdAndModelId(1, "default"))
                .thenReturn(Optional.of(sourceEmb));
        
        when(itemRepository.findVisibleIdsByLibraryIdsIn(Set.of(10))).thenReturn(List.of(1, 2));
        when(embeddingRepository.findByModelIdAndMediaItemIdIn("default", List.of(1, 2)))
                .thenReturn(List.of(sourceEmb, otherEmb));

        List<EmbeddingIndexService.Scored> results =
                embeddingIndexService.searchSimilarToItem(1, Set.of(10), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).itemId()).isEqualTo(2);
    }

    private static byte[] toBytes(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4);
        for (float v : values) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }
}
