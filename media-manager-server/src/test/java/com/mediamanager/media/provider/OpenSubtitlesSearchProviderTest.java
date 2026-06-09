package com.mediamanager.media.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.common.service.RateLimiterService;
import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.dto.SubtitleSearchResultDto;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.spi.SubtitleSearchProvider;
import com.mediamanager.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenSubtitlesSearchProviderTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private SysConfigService sysConfigService;
    @Mock
    private StoragePathMapper storagePathMapper;

    private OpenSubtitlesSearchProvider provider;

    @BeforeEach
    void setUp() {
        RateLimiterService rateLimiterService = new RateLimiterService() {
            @Override
            public <T> T executeWithRateLimit(String key, int maxConcurrent, Callable<T> task) {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        provider = new OpenSubtitlesSearchProvider(
                restTemplate,
                new ObjectMapper(),
                sysConfigService,
                rateLimiterService,
                storagePathMapper);
    }

    @Test
    void isConfiguredRequiresApiKey() {
        when(sysConfigService.opensubtitlesApiKey()).thenReturn("");
        assertThat(provider.isConfigured()).isFalse();

        when(sysConfigService.opensubtitlesApiKey()).thenReturn("test-key");
        assertThat(provider.isConfigured()).isTrue();
    }

    @Test
    void searchReturnsEmptyWhenNotConfigured() {
        when(sysConfigService.opensubtitlesApiKey()).thenReturn("");
        MediaItem item = new MediaItem();
        item.setTitle("Test Movie");
        SubtitleSearchProvider.SearchContext context =
                new SubtitleSearchProvider.SearchContext(item, null, "Test Movie", "zh-CN");

        assertThat(provider.search(context)).isEmpty();
    }

    @Test
    void searchParsesProviderResponse() {
        when(sysConfigService.opensubtitlesApiKey()).thenReturn("test-key");
        when(sysConfigService.opensubtitlesBaseUrl()).thenReturn("https://api.opensubtitles.com/api/v1");
        when(sysConfigService.subtitleUserAgent()).thenReturn("MediaManager/1.0");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {
                          "data": [
                            {
                              "id": "1",
                              "attributes": {
                                "subtitle_id": "1001",
                                "language": "zh-CN",
                                "release": "Test Movie zh",
                                "download_count": 42,
                                "files": [
                                  { "file_id": 1001, "file_name": "test.srt" }
                                ]
                              }
                            }
                          ]
                        }
                        """));

        MediaItem item = new MediaItem();
        item.setTitle("Test Movie");
        List<SubtitleSearchResultDto> results = provider.search(
                new SubtitleSearchProvider.SearchContext(item, null, "Test Movie", "zh-CN"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getProvider()).isEqualTo(OpenSubtitlesSearchProvider.PROVIDER_ID);
        assertThat(results.get(0).getExternalId()).isEqualTo("1001");
        assertThat(results.get(0).getLanguage()).isEqualTo("zh-CN");
    }

    @Test
    void downloadFailsWhenNotConfigured() {
        when(sysConfigService.opensubtitlesApiKey()).thenReturn("");
        assertThatThrownBy(() -> provider.download("1001"))
                .isInstanceOf(com.mediamanager.common.exception.BusinessException.class);
    }
}
