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
        service = new AiTagSemanticMergeService(
                aiOrchestrator,
                new TagCanonicalizationService(tagRepository),
                new TagQualityService(),
                new ObjectMapper());
    }

    @Test
    void returnsOnlyHighConfidenceValidGroups() {
        List<AiOrganizationWorker.TagSnapshot> tags = List.of(
                new AiOrganizationWorker.TagSnapshot(1, "\u62a5\u9053"),
                new AiOrganizationWorker.TagSnapshot(2, "\u62a5\u5bfc"),
                new AiOrganizationWorker.TagSnapshot(3, "\u6309\u6469"),
                new AiOrganizationWorker.TagSnapshot(4, "\u6309\u6469\u68d2"),
                new AiOrganizationWorker.TagSnapshot(5, "\u62a5\u590d"));
        when(aiOrchestrator.resolve(9, AiTaskType.COMPLETE_METADATA)).thenReturn(provider);
        when(aiOrchestrator.defaultConfig(9, AiTaskType.COMPLETE_METADATA)).thenReturn(Map.of());
        when(provider.providerId()).thenReturn("openai-compatible");
        when(provider.completeMetadata(anyString(), anyMap()))
                .thenReturn(Optional.of("""
                        [
                          {"canonicalId":1,"duplicateIds":[2],"confidence":0.94},
                          {"canonicalId":3,"duplicateIds":[4],"confidence":0.6},
                          {"canonicalId":1,"duplicateIds":[5],"confidence":0.91},
                          {"canonicalId":99,"duplicateIds":[2],"confidence":0.99}
                        ]
                        """));

        List<AiTagSemanticMergeService.SemanticMergeGroup> groups = service.suggestGroups(9, tags);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().canonicalId()).isEqualTo(1);
        assertThat(groups.getFirst().duplicateIds()).containsExactly(2);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(provider).completeMetadata(prompt.capture(), anyMap());
        assertThat(prompt.getValue())
                .contains("\u6309\u6469 vs \u6309\u6469\u68d2")
                .contains("\u62a5\u5bfc/\u62a5\u9053");
    }
}
