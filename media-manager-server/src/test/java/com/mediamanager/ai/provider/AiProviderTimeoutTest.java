package com.mediamanager.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderTimeoutTest {

    @Mock
    private AiHttpClientFactory httpClientFactory;

    @Mock
    private RestTemplate requestClient;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void openAiCompatibleProviderUsesConfiguredTimeout() {
        when(httpClientFactory.create(123_456L)).thenReturn(requestClient);
        when(requestClient.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(httpClientFactory, objectMapper);

        assertThat(provider.completeMetadata("prompt", Map.of("timeoutMs", 123_456L)))
                .contains("ok");
        verify(httpClientFactory).create(123_456L);
    }

    @Test
    void openAiCompatibleProviderDefaultsToTenMinutes() {
        when(httpClientFactory.create(AiHttpClientFactory.DEFAULT_TIMEOUT_MS)).thenReturn(requestClient);
        when(requestClient.postForObject(
                anyString(),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(httpClientFactory, objectMapper);

        assertThat(provider.completeMetadata("prompt", Map.of())).contains("ok");
        verify(httpClientFactory).create(600_000L);
    }

    @Test
    void ollamaProviderUsesConfiguredTimeout() {
        when(httpClientFactory.create(600_000L)).thenReturn(requestClient);
        when(requestClient.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"response\":\"ok\"}");

        OllamaAiProvider provider = new OllamaAiProvider(httpClientFactory, objectMapper);

        assertThat(provider.completeMetadata("prompt", Map.of("timeoutMs", 600_000L)))
                .contains("ok");
        verify(httpClientFactory).create(600_000L);
    }
}
