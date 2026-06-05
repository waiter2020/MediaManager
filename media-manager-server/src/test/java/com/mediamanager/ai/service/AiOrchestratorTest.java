package com.mediamanager.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.ai.AiTaskType;
import com.mediamanager.ai.provider.NoopAiProvider;
import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import com.mediamanager.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorTest {

    @Mock
    private AiProvider ollamaProvider;
    @Mock
    private AiProvider openAiProvider;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private LibraryPluginConfigRepository pluginConfigRepository;
    @Mock
    private AiSuggestionService aiSuggestionService;
    @Mock
    private MediaItemRepository mediaItemRepository;
    @Mock
    private AiHealthCheckScheduler healthCheckScheduler;

    private AiOrchestrator aiOrchestrator;

    @BeforeEach
    void setUp() {
        NoopAiProvider noopAiProvider = new NoopAiProvider();
        aiOrchestrator = new AiOrchestrator(
                List.of(ollamaProvider, openAiProvider, noopAiProvider),
                noopAiProvider,
                sysConfigService,
                pluginConfigRepository,
                aiSuggestionService,
                mediaItemRepository,
                new ObjectMapper(),
                healthCheckScheduler);
        ReflectionTestUtils.setField(aiOrchestrator, "yamlDefaultProviderId", "ollama");
        ReflectionTestUtils.setField(aiOrchestrator, "yamlLlmProviderId", "ollama");
        ReflectionTestUtils.setField(aiOrchestrator, "yamlEmbedProviderId", "ollama");
        ReflectionTestUtils.setField(aiOrchestrator, "yamlOllamaBaseUrl", "http://localhost:11434");

        when(ollamaProvider.providerId()).thenReturn("ollama");
        when(ollamaProvider.supports(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == AiTaskType.EMBED_TEXT);
        when(openAiProvider.providerId()).thenReturn("openai-compatible");
        when(openAiProvider.supports(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == AiTaskType.NL_QUERY);
    }

    @Test
    void routesLlmAndEmbeddingTasksToDifferentConfiguredProviders() {
        Map<String, Object> config = new HashMap<>();
        config.put("providerId", "ollama");
        config.put("llmProviderId", "openai-compatible");
        config.put("embedProviderId", "ollama");
        config.put("ollamaBaseUrl", "http://ollama:11434");
        config.put("openaiBaseUrl", "https://api.example.com/v1");
        config.put("llmApiKey", "llm-secret");
        config.put("llmModel", "gpt-4o-mini");
        config.put("embedModel", "nomic-embed-text");
        config.put("outboundAllowed", true);
        when(sysConfigService.aiConfigOverrides()).thenReturn(config);

        assertThat(aiOrchestrator.resolve(AiTaskType.EMBED_TEXT).providerId()).isEqualTo("ollama");
        assertThat(aiOrchestrator.resolve(AiTaskType.NL_QUERY).providerId()).isEqualTo("openai-compatible");

        Map<String, Object> embedConfig = aiOrchestrator.defaultConfig(AiTaskType.EMBED_TEXT);
        assertThat(embedConfig.get("providerId")).isEqualTo("ollama");
        assertThat(embedConfig.get("baseUrl")).isEqualTo("http://ollama:11434");

        Map<String, Object> llmConfig = aiOrchestrator.defaultConfig(AiTaskType.NL_QUERY);
        assertThat(llmConfig.get("providerId")).isEqualTo("openai-compatible");
        assertThat(llmConfig.get("baseUrl")).isEqualTo("https://api.example.com/v1");
        assertThat(llmConfig.get("apiKey")).isEqualTo("llm-secret");
    }

    @Test
    void selectsSeparateOpenAiCompatibleCredentialsForLlmAndEmbeddingTasks() {
        when(openAiProvider.supports(AiTaskType.EMBED_TEXT)).thenReturn(true);

        Map<String, Object> config = new HashMap<>();
        config.put("providerId", "openai-compatible");
        config.put("llmProviderId", "openai-compatible");
        config.put("embedProviderId", "openai-compatible");
        config.put("openaiBaseUrl", "https://legacy.example.com/v1");
        config.put("openaiLlmBaseUrl", "https://llm.example.com/v1");
        config.put("openaiEmbedBaseUrl", "https://embed.example.com/v1");
        config.put("apiKey", "legacy-secret");
        config.put("llmApiKey", "llm-secret");
        config.put("embedApiKey", "embed-secret");
        config.put("llmModel", "gpt-4o-mini");
        config.put("embedModel", "text-embedding-3-small");
        config.put("outboundAllowed", true);
        when(sysConfigService.aiConfigOverrides()).thenReturn(config);

        assertThat(aiOrchestrator.resolve(AiTaskType.NL_QUERY).providerId()).isEqualTo("openai-compatible");
        assertThat(aiOrchestrator.resolve(AiTaskType.EMBED_TEXT).providerId()).isEqualTo("openai-compatible");

        Map<String, Object> llmConfig = aiOrchestrator.defaultConfig(AiTaskType.NL_QUERY);
        assertThat(llmConfig.get("baseUrl")).isEqualTo("https://llm.example.com/v1");
        assertThat(llmConfig.get("apiKey")).isEqualTo("llm-secret");

        Map<String, Object> embedConfig = aiOrchestrator.defaultConfig(AiTaskType.EMBED_TEXT);
        assertThat(embedConfig.get("baseUrl")).isEqualTo("https://embed.example.com/v1");
        assertThat(embedConfig.get("apiKey")).isEqualTo("embed-secret");
    }
}
