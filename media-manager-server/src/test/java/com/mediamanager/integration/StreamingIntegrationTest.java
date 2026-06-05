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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private byte[] mp4Bytes;

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
        mp4Bytes = "fake-mp4-content-for-test".getBytes(StandardCharsets.UTF_8);
        Files.write(mediaPath, mp4Bytes);

        MediaLibrary lib = createLibrary("StreamLib");
        MediaItem item = createItem(lib, "Sample Movie");
        mp4File = MediaFile.builder()
                .mediaItem(item)
                .filePath(mediaPath.toString())
                .fileName("sample.mp4")
                .fileSize((long) mp4Bytes.length)
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

    @Test
    void streamWithoutRangeReturnsFullVideo() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(content().bytes(mp4Bytes));
    }

    @Test
    void streamRangeReturnsPartialVideoBytes() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId())
                        .header("Authorization", token)
                        .header("Range", "bytes=5-11"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().string("Content-Range", "bytes 5-11/" + mp4Bytes.length))
                .andExpect(content().bytes("mp4-con".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void streamRangeOutsideFileReturnsRequestedRangeNotSatisfiable() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId())
                        .header("Authorization", token)
                        .header("Range", "bytes=999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + mp4Bytes.length));
    }
}
