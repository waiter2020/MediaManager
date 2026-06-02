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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StreamingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    private String token;
    private MediaFile mp4File;

    @BeforeEach
    void setUp() throws Exception {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser user = createUser("streamer", "SUPER_ADMIN");
        token = bearerToken(user, Set.of("media:view", "media:play"));

        Path mediaPath = tempDir.resolve("sample.mp4");
        Files.writeString(mediaPath, "fake-mp4-content-for-test");

        MediaLibrary lib = createLibrary("StreamLib");
        MediaItem item = createItem(lib, "Sample Movie");
        mp4File = MediaFile.builder()
                .mediaItem(item)
                .filePath(mediaPath.toString())
                .fileName("sample.mp4")
                .fileSize(32L)
                .container("mp4")
                .mimeType("video/mp4")
                .deleted(false)
                .build();
        mp4File = mediaFileRepository.save(mp4File);
    }

    @Test
    void playbackInfoReturnsDirectForMp4() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId() + "/playback")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.url").value("/api/v1/stream/raw/" + mp4File.getId()));
    }
}
