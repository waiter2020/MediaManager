package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PreviewPlaybackIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    private String token;
    private MediaFile mp4File;
    private MediaFile wmvFile;
    private MediaFile mkvFile;

    @BeforeEach
    void setUp() throws Exception {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser user = createUser("preview-user", "SUPER_ADMIN");
        token = bearerToken(user, Set.of("media:view", "media:play"));

        MediaLibrary lib = createLibrary("PreviewLib");
        MediaItem item = createItem(lib, "Preview Sample");

        Path mp4Path = tempDir.resolve("sample.mp4");
        Files.write(mp4Path, "fake-mp4-content".getBytes(StandardCharsets.UTF_8));
        mp4File = mediaFileRepository.save(MediaFile.builder()
                .mediaItem(item)
                .filePath(mp4Path.toString())
                .fileName("sample.mp4")
                .fileSize(Files.size(mp4Path))
                .container("mp4")
                .videoCodec("h264")
                .audioCodec("aac")
                .durationSeconds(7200)
                .mimeType("video/mp4")
                .deleted(false)
                .build());

        Path wmvPath = tempDir.resolve("sample.wmv");
        Files.write(wmvPath, "fake-wmv-content".getBytes(StandardCharsets.UTF_8));
        wmvFile = mediaFileRepository.save(MediaFile.builder()
                .mediaItem(item)
                .filePath(wmvPath.toString())
                .fileName("sample.wmv")
                .fileSize(Files.size(wmvPath))
                .container("wmv")
                .videoCodec("wmv3")
                .audioCodec("wma")
                .durationSeconds(3600)
                .mimeType("video/x-ms-wmv")
                .deleted(false)
                .build());

        Path mkvPath = tempDir.resolve("sample.mkv");
        Files.write(mkvPath, "fake-mkv-content".getBytes(StandardCharsets.UTF_8));
        mkvFile = mediaFileRepository.save(MediaFile.builder()
                .mediaItem(item)
                .filePath(mkvPath.toString())
                .fileName("sample.mkv")
                .fileSize(Files.size(mkvPath))
                .container("mkv")
                .videoCodec("h264")
                .audioCodec("aac")
                .durationSeconds(5400)
                .mimeType("video/x-matroska")
                .deleted(false)
                .build());
    }

    @Test
    void previewPlaybackReturnsDirectForMp4() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId() + "/playback")
                        .header("Authorization", token)
                        .param("purpose", "preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.directPlayable").value(true))
                .andExpect(jsonPath("$.data.url").value(containsString("/stream/" + mp4File.getId())))
                .andExpect(jsonPath("$.data.url").value(not(containsString("/hls/"))))
                .andExpect(jsonPath("$.data.transcoding").value(false))
                .andExpect(jsonPath("$.data.transcodingReasons").value(containsString("PreviewRequested")));
    }

    @Test
    void previewPlaybackReturnsDirectButNotPlayableForWmv() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + wmvFile.getId() + "/playback")
                        .header("Authorization", token)
                        .param("purpose", "preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.directPlayable").value(false))
                .andExpect(jsonPath("$.data.url").value(containsString("/stream/" + wmvFile.getId())))
                .andExpect(jsonPath("$.data.transcoding").value(false))
                .andExpect(jsonPath("$.data.transcodingReasons").value(containsString("ContainerNotSupported")));
    }

    @Test
    void previewPlaybackReturnsDirectButNotPlayableForMkvH264() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mkvFile.getId() + "/playback")
                        .header("Authorization", token)
                        .param("purpose", "preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.directPlayable").value(false))
                .andExpect(jsonPath("$.data.url").value(containsString("/stream/" + mkvFile.getId())))
                .andExpect(jsonPath("$.data.transcoding").value(false))
                .andExpect(jsonPath("$.data.transcodingReasons").value(containsString("ContainerNotSupported")));
    }

    @Test
    void previewPlaybackIgnoresStartOffsetForDirectOnlyPreview() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + wmvFile.getId() + "/playback")
                        .header("Authorization", token)
                        .param("purpose", "preview")
                        .param("start", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.startOffset").value(0))
                .andExpect(jsonPath("$.data.url").value(containsString("/stream/" + wmvFile.getId())))
                .andExpect(jsonPath("$.data.url").value(not(containsString("start=90"))));
    }

    @Test
    void defaultPurposeKeepsPlaybackBehavior() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId() + "/playback")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.transcodingReasons[?(@=='PreviewRequested')]").isEmpty());
    }
}
