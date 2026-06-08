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

    private TagCanonicalizationService service;

    @BeforeEach
    void setUp() {
        service = new TagCanonicalizationService(tagRepository);
    }

    @Test
    void normalizesInvisibleCharactersAndChineseVariants() {
        assertThat(service.semanticKey("\u200b\u62f7\u554f")).isEqualTo(service.semanticKey("\u62f7\u95ee"));
        assertThat(service.semanticKey("\u5831\u5c0e")).isEqualTo(service.semanticKey("\u62a5\u9053"));
        assertThat(service.semanticKey("\u8b77\u58eb")).isEqualTo(service.semanticKey("\u62a4\u58eb"));
    }

    @Test
    void mapsConservativeChineseSynonymsToSharedKeys() {
        assertThat(service.semanticKey("\u62d8\u675f")).isEqualTo(service.semanticKey("\u6346\u7ed1"));
        assertThat(service.semanticKey("\u6279\u5224")).isEqualTo(service.semanticKey("\u6279\u8bc4"));
        assertThat(service.semanticKey("\u62a5\u590d")).isEqualTo(service.semanticKey("\u590d\u4ec7"));
        assertThat(service.semanticKey("\u5c0f\u4fbf")).isEqualTo(service.semanticKey("\u6392\u5c3f"));
        assertThat(service.semanticKey("\u62cd\u5c41\u80a1")).isEqualTo(service.semanticKey("\u6253\u5c41\u80a1"));
    }

    @Test
    void keepsRelatedButDifferentTagsSeparate() {
        assertThat(service.semanticKey("\u6309\u6469")).isNotEqualTo(service.semanticKey("\u6309\u6469\u68d2"));
        assertThat(service.semanticKey("\u6311\u6218")).isNotEqualTo(service.semanticKey("\u6311\u8845"));
        assertThat(service.semanticKey("\u6392\u4fbf")).isNotEqualTo(service.semanticKey("\u6392\u5c3f"));
    }
}
