package com.mediamanager.classification.service;

import com.mediamanager.classification.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TagSimilarityServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagSimilarityService service;

    @BeforeEach
    void setUp() {
        TagCanonicalizationService canonicalizationService = new TagCanonicalizationService(
                tagRepository,
                mock(TagSimilarityService.class),
                mock(TagEmbeddingClusterService.class));
        service = new TagSimilarityService(canonicalizationService);
    }

    @Test
    void clustersPrefixVariantsInAggressiveMode() {
        List<TagSimilarityService.SimilarTagCluster> clusters = service.clusterByStructure(
                List.of(
                        new TagMergeSnapshot(1, "\u9a91\u4e58", 3L, "AI"),
                        new TagMergeSnapshot(2, "\u9a91\u4e58\u4f4d", 8L, "MANUAL")),
                MergeAggressiveness.AGGRESSIVE);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().canonicalId()).isEqualTo(2);
        assertThat(clusters.getFirst().memberIds()).containsExactly(1);
    }

    @Test
    void keepsCompoundDrinkTagsSeparate() {
        assertThat(service.shouldBlockMerge("\u996e\u7cbe", "\u996e\u9152")).isTrue();
        assertThat(service.clusterByStructure(
                List.of(
                        new TagMergeSnapshot(1, "\u996e\u7cbe", 1L, "AI"),
                        new TagMergeSnapshot(2, "\u996e\u9152", 1L, "AI")),
                MergeAggressiveness.AGGRESSIVE)).isEmpty();
    }

    @Test
    void keepsMassageAndVibratorSeparate() {
        assertThat(service.shouldBlockMerge("\u6309\u6469", "\u6309\u6469\u68d2")).isTrue();
        assertThat(service.clusterByStructure(
                List.of(
                        new TagMergeSnapshot(1, "\u6309\u6469", 1L, "AI"),
                        new TagMergeSnapshot(2, "\u6309\u6469\u68d2", 1L, "AI")),
                MergeAggressiveness.AGGRESSIVE)).isEmpty();
    }
}
