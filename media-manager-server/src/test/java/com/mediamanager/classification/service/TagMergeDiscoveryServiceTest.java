package com.mediamanager.classification.service;

import com.mediamanager.ai.service.AiTagSemanticMergeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagMergeDiscoveryServiceTest {

    @Mock
    private TagCanonicalizationService tagCanonicalizationService;
    @Mock
    private TagSimilarityService tagSimilarityService;
    @Mock
    private TagEmbeddingClusterService tagEmbeddingClusterService;
    @Mock
    private AiTagSemanticMergeService tagSemanticMergeService;

    private TagMergeDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new TagMergeDiscoveryService(
                tagCanonicalizationService,
                tagSimilarityService,
                tagEmbeddingClusterService,
                tagSemanticMergeService);
    }

    @Test
    void previewScopeSkipsEmbeddingAndAi() {
        List<TagMergeSnapshot> tags = List.of(
                new TagMergeSnapshot(1, "\u9a91\u4e58", 1L, "AI"),
                new TagMergeSnapshot(2, "\u9a91\u4e58\u4f4d", 2L, "MANUAL"));
        when(tagCanonicalizationService.semanticKey(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagSimilarityService.clusterByStructure(any(), eq(MergeAggressiveness.AGGRESSIVE)))
                .thenReturn(List.of(new TagSimilarityService.SimilarTagCluster(2, List.of(1), 0.95, "prefix")));

        List<TagMergeDiscoveryService.DiscoveredMergeGroup> groups =
                service.discoverGroups(tags, MergeAggressiveness.AGGRESSIVE, 1, DiscoveryScope.PREVIEW);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().source()).isEqualTo("STRUCTURE");
        verify(tagEmbeddingClusterService, never()).clusterByEmbedding(any(), anyInt());
        verify(tagSemanticMergeService, never()).suggestGroups(anyInt(), any(), any());
    }

    @Test
    void applyMergeScopeUsesEmbeddingButNotAiInDiscoverGroups() {
        List<TagMergeSnapshot> tags = List.of(
                new TagMergeSnapshot(10, "\u62a5\u9053", 1L, "AI"),
                new TagMergeSnapshot(11, "\u62a5\u5bfc", 2L, "AI"));
        when(tagCanonicalizationService.semanticKey(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagSimilarityService.clusterByStructure(any(), any())).thenReturn(List.of());
        when(tagEmbeddingClusterService.clusterByEmbedding(any(), eq(1)))
                .thenReturn(List.of(new TagEmbeddingClusterService.EmbeddingCluster(10, List.of(11), 0.96, "embedding")));

        List<TagMergeDiscoveryService.DiscoveredMergeGroup> groups =
                service.discoverGroups(tags, MergeAggressiveness.AGGRESSIVE, 1, DiscoveryScope.APPLY_MERGE);

        assertThat(groups).extracting(TagMergeDiscoveryService.DiscoveredMergeGroup::source)
                .contains("EMBEDDING");
        verify(tagSemanticMergeService, never()).suggestGroups(anyInt(), any(), any());
    }
}
