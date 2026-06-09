package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.classification.service.MergeAggressiveness;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.service.TagMergeSnapshot;
import com.mediamanager.classification.service.TagQualityService;
import com.mediamanager.classification.service.TagSimilarityService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTagSemanticMergeServiceTest {

    @Mock
    private AiOrchestrator aiOrchestrator;
    @Mock
    private AiProvider provider;
    @Mock
    private TagRepository tagRepository;

    private AiTagSemanticMergeService service;

    @BeforeEach
    void setUp() {
        TagCanonicalizationService canonicalizationService = new TagCanonicalizationService(
                tagRepository,
                mock(TagSimilarityService.class),
                mock(com.mediamanager.classification.service.TagEmbeddingClusterService.class));
        service = new AiTagSemanticMergeService(
                aiOrchestrator,
                canonicalizationService,
                new TagQualityService(),
                new TagSimilarityService(canonicalizationService),
                new ObjectMapper());
    }

    @Test
    void returnsOnlyHighConfidenceValidGroupsForClusterPrompt() {
        List<AiTagSemanticMergeService.ClusterTag> cluster = List.of(
                new AiTagSemanticMergeService.ClusterTag(1, "\u62a5\u9053"),
                new AiTagSemanticMergeService.ClusterTag(2, "\u62a5\u5bfc"),
                new AiTagSemanticMergeService.ClusterTag(3, "\u6309\u6469"),
                new AiTagSemanticMergeService.ClusterTag(4, "\u6309\u6469\u68d2"),
                new AiTagSemanticMergeService.ClusterTag(5, "\u62a5\u590d"));
        when(aiOrchestrator.resolve(9, AiTaskType.COMPLETE_METADATA)).thenReturn(provider);
        when(aiOrchestrator.defaultConfig(9, AiTaskType.COMPLETE_METADATA)).thenReturn(Map.of());
        when(provider.providerId()).thenReturn("openai-compatible");
        when(provider.completeMetadata(anyString(), anyMap()))
                .thenReturn(Optional.of("""
                        [
                          {"canonicalId":1,"duplicateIds":[2],"confidence":0.94,"reason":"same concept"},
                          {"canonicalId":3,"duplicateIds":[4],"confidence":0.6,"reason":"related"},
                          {"canonicalId":1,"duplicateIds":[5],"confidence":0.91,"reason":"same concept"},
                          {"canonicalId":99,"duplicateIds":[2],"confidence":0.99,"reason":"invalid"}
                        ]
                        """));

        List<AiTagSemanticMergeService.SemanticMergeGroup> groups =
                service.suggestGroups(9, List.of(cluster), MergeAggressiveness.AGGRESSIVE);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().canonicalId()).isEqualTo(1);
        assertThat(groups.getFirst().duplicateIds()).containsExactly(2);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).completeMetadata(prompt.capture(), anyMap());
        assertThat(prompt.getValue())
                .contains("\u996e\u7cbe/\u996e\u9152/\u996e\u5c3f")
                .contains("\u9a91\u4e58/\u9a91\u4e58\u4f4d");
    }

    @Test
    void buildsCandidateClustersFromStructuralMatches() {
        List<TagMergeSnapshot> tags = List.of(
                new TagMergeSnapshot(10, "\u9a91\u4e58", 1L, "AI"),
                new TagMergeSnapshot(11, "\u9a91\u4e58\u4f4d", 2L, "AI"),
                new TagMergeSnapshot(12, "\u9996\u6b21", 1L, "AI"),
                new TagMergeSnapshot(13, "\u9996\u6b21\u62cd\u6444", 2L, "AI"));

        List<List<AiTagSemanticMergeService.ClusterTag>> clusters =
                service.buildCandidateClusters(tags, MergeAggressiveness.AGGRESSIVE);

        assertThat(clusters).isNotEmpty();
        assertThat(clusters.stream().flatMap(List::stream).map(AiTagSemanticMergeService.ClusterTag::id))
                .contains(10, 11);
    }
}
