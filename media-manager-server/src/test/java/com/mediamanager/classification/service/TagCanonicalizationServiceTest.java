package com.mediamanager.classification.service;

import com.mediamanager.classification.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TagCanonicalizationServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private TagSimilarityService tagSimilarityService;
    @Mock
    private TagEmbeddingClusterService tagEmbeddingClusterService;

    private TagCanonicalizationService service;

    @BeforeEach
    void setUp() {
        service = new TagCanonicalizationService(
                tagRepository, tagSimilarityService, tagEmbeddingClusterService);
    }

    @Test
    void normalizesInvisibleCharactersAndChineseVariants() {
        assertThat(service.semanticKey("\u200b\u62f7\u554f")).isEqualTo(service.semanticKey("\u62f7\u95ee"));
        assertThat(service.semanticKey("\u5831\u5c0e")).isEqualTo(service.semanticKey("\u62a5\u9053"));
        assertThat(service.semanticKey("\u8b77\u58eb")).isEqualTo(service.semanticKey("\u62a4\u58eb"));
    }

    @Test
    void keepsDistinctSynonymsOnDifferentNormalizedKeys() {
        assertThat(service.semanticKey("\u62d8\u675f")).isNotEqualTo(service.semanticKey("\u6346\u7ed1"));
        assertThat(service.semanticKey("\u6279\u5224")).isNotEqualTo(service.semanticKey("\u6279\u8bc4"));
        assertThat(service.semanticKey("\u62a5\u590d")).isNotEqualTo(service.semanticKey("\u590d\u4ec7"));
    }

    @Test
    void keepsRelatedButDifferentTagsSeparate() {
        assertThat(service.semanticKey("\u6309\u6469")).isNotEqualTo(service.semanticKey("\u6309\u6469\u68d2"));
        assertThat(service.semanticKey("\u6311\u6218")).isNotEqualTo(service.semanticKey("\u6311\u8845"));
        assertThat(service.semanticKey("\u6392\u4fbf")).isNotEqualTo(service.semanticKey("\u6392\u5c3f"));
        assertThat(service.semanticKey("\u996e\u7cbe")).isNotEqualTo(service.semanticKey("\u996e\u9152"));
    }
}
