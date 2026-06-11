package com.mediamanager.streaming.service;

import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HlsStreamingServicePreviewTest {

    @Mock
    private HardwareAccelerationService hardwareAccelerationService;

    @InjectMocks
    private HlsStreamingService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "yamlFfmpegPath", "ffmpeg");
        ReflectionTestUtils.setField(service, "cacheDir", "./data/cache");
        ReflectionTestUtils.setField(service, "segmentDuration", 6);
        ReflectionTestUtils.setField(service, "playlistWaitSecondsStreamCopy", 10);
        ReflectionTestUtils.setField(service, "playlistWaitSecondsTranscode", 90);
        ReflectionTestUtils.setField(service, "segmentWaitSeconds", 30);
        ReflectionTestUtils.setField(service, "idleStopSeconds", 120);
        ReflectionTestUtils.setField(service, "previewIdleStopSeconds", 45);
    }

    @Test
    void buildPreviewTranscodePlanUsesStreamCopyForMkvH264() throws Exception {
        MediaFile file = mkvH264File();

        Object plan = invokeBuildPreviewTranscodePlan(file);

        assertThat(strategyName(plan)).isEqualTo("STREAM_COPY");
    }

    @Test
    void buildPreviewTranscodePlanUsesSoftwareWhenEncodingRequired() throws Exception {
        MediaFile file = hevcFile();

        Object plan = invokeBuildPreviewTranscodePlan(file);

        assertThat(strategyName(plan)).isEqualTo("SOFTWARE");
    }

    @Test
    void isPreviewProcessKeyDetects360pVariant() throws Exception {
        Method method = HlsStreamingService.class.getDeclaredMethod("isPreviewProcessKey", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "12:auto-360p")).isEqualTo(true);
        assertThat(method.invoke(service, "12:software-360p:s90")).isEqualTo(true);
        assertThat(method.invoke(service, "12:auto-auto")).isEqualTo(false);
        assertThat(method.invoke(service, "12:hardware-480p")).isEqualTo(false);
    }

    private Object invokeBuildPreviewTranscodePlan(MediaFile file) throws Exception {
        Method method = HlsStreamingService.class.getDeclaredMethod("buildPreviewTranscodePlan", MediaFile.class);
        method.setAccessible(true);
        return method.invoke(service, file);
    }

    private String strategyName(Object plan) throws Exception {
        Method strategy = plan.getClass().getDeclaredMethod("strategy");
        strategy.setAccessible(true);
        return strategy.invoke(plan).toString();
    }

    private MediaFile mkvH264File() {
        return MediaFile.builder()
                .mediaItem(MediaItem.builder().id(1).build())
                .container("mkv")
                .videoCodec("h264")
                .audioCodec("aac")
                .width(1920)
                .height(1080)
                .mimeType("video/x-matroska")
                .build();
    }

    private MediaFile hevcFile() {
        return MediaFile.builder()
                .mediaItem(MediaItem.builder().id(2).build())
                .container("mkv")
                .videoCodec("hevc")
                .audioCodec("aac")
                .width(3840)
                .height(2160)
                .mimeType("video/x-matroska")
                .build();
    }
}
