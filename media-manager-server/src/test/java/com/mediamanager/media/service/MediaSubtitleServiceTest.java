package com.mediamanager.media.service;

import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaSubtitleRepository;
import com.mediamanager.media.spi.SubtitleSearchProvider;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.system.service.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MediaSubtitleServiceTest {

    @Mock
    private MediaSubtitleRepository subtitleRepository;

    @Mock
    private MediaFileRepository fileRepository;

    @Mock
    private MediaItemRepository itemRepository;

    @Mock
    private LibraryAccessService libraryAccessService;

    @Mock
    private StoragePathMapper storagePathMapper;

    @Mock
    private List<SubtitleSearchProvider> searchProviders;

    @Mock
    private SysConfigService sysConfigService;

    @InjectMocks
    private MediaSubtitleService subtitleService;

    @TempDir
    Path tempDir;

    @Test
    void convertSrtToVtt_stripsHtmlTags() throws IOException {
        String srt = """
                1
                00:00:01,000 --> 00:00:03,000
                <i>Hello</i> &amp; <b>world</b>

                """;

        String vtt = convertFromSrt(srt);

        assertTrue(vtt.contains("WEBVTT"));
        assertTrue(vtt.contains("Hello & world"));
        assertFalse(vtt.contains("<i>"));
        assertFalse(vtt.contains("<b>"));
    }

    @Test
    void convertAssToVtt_mergesSameTimestampLines() throws IOException {
        String ass = """
                [Script Info]
                ScriptType: v4.00+

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\\an8}Top line
                Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Bottom line
                """;

        String vtt = convertFromAss(ass);

        assertTrue(vtt.contains("00:00:01.000 --> 00:00:03.000"));
        assertTrue(vtt.contains("Top line\nBottom line") || vtt.contains("Top line\r\nBottom line"));
        assertFalse(vtt.contains("{\\an8}"));
    }

    @Test
    void shiftWebVtt_appliesPlaybackOffset() {
        String vtt = """
                WEBVTT

                01:00:01.000 --> 01:00:03.000
                Hello
                """;

        String shifted = MediaSubtitleService.shiftWebVtt(vtt, 3600, 0);

        assertTrue(shifted.contains("00:00:01.000 --> 00:00:03.000"));
        assertFalse(shifted.contains("01:00:01.000"));
    }

    @Test
    void shiftWebVtt_appliesUserDelay() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                Hello
                """;

        String shifted = MediaSubtitleService.shiftWebVtt(vtt, 0, 2);

        assertTrue(shifted.contains("00:00:03.000 --> 00:00:05.000"));
    }

    @Test
    void shiftWebVtt_dropsCuesEndingBeforeZero() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:03.000
                Before offset

                01:00:01.000 --> 01:00:03.000
                After offset
                """;

        String shifted = MediaSubtitleService.shiftWebVtt(vtt, 3600, 0);

        assertFalse(shifted.contains("Before offset"));
        assertTrue(shifted.contains("After offset"));
    }

    private String convertFromSrt(String srt) throws IOException {
        Path path = tempDir.resolve("sample.srt");
        Files.writeString(path, srt, StandardCharsets.UTF_8);
        return ReflectionTestUtils.invokeMethod(subtitleService, "toWebVtt", path, "srt");
    }

    private String convertFromAss(String ass) throws IOException {
        Path path = tempDir.resolve("sample.ass");
        Files.writeString(path, ass, StandardCharsets.UTF_8);
        return ReflectionTestUtils.invokeMethod(subtitleService, "toWebVtt", path, "ass");
    }
}
