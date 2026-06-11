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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HlsSeekIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    private String token;
    private MediaFile wmvFile;
    private MediaFile mp4File;

    @BeforeEach
    void setUp() throws Exception {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser user = createUser("hls-seeker", "SUPER_ADMIN");
        token = bearerToken(user, Set.of("media:view", "media:play"));

        MediaLibrary lib = createLibrary("SeekLib");
        MediaItem item = createItem(lib, "WMV Sample");

        Path wmvPath = tempDir.resolve("sample.wmv");
        Files.write(wmvPath, "fake-wmv-content".getBytes(StandardCharsets.UTF_8));
        wmvFile = MediaFile.builder()
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
                .build();
        wmvFile = mediaFileRepository.save(wmvFile);

        Path mp4Path = tempDir.resolve("sample.mp4");
        Files.write(mp4Path, "fake-mp4-content".getBytes(StandardCharsets.UTF_8));
        mp4File = MediaFile.builder()
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
                .build();
        mp4File = mediaFileRepository.save(mp4File);
    }

    @Test
    void playbackInfoReturnsHlsForWmvWithDuration() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + wmvFile.getId() + "/playback")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("hls"))
                .andExpect(jsonPath("$.data.durationSeconds").value(3600))
                .andExpect(jsonPath("$.data.directPlayable").value(false))
                .andExpect(jsonPath("$.data.url").value(containsString("/hls/")));
    }

    @Test
    void playbackInfoReturnsDirectForMp4() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + mp4File.getId() + "/playback")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("direct"))
                .andExpect(jsonPath("$.data.durationSeconds").value(7200))
                .andExpect(jsonPath("$.data.directPlayable").value(true));
    }

    @Test
    void playbackInfoAcceptsStartOffsetForHls() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + wmvFile.getId() + "/playback")
                        .header("Authorization", token)
                        .param("start", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("hls"))
                .andExpect(jsonPath("$.data.startOffset").value(120))
                .andExpect(jsonPath("$.data.url").value(containsString("start=120")));
    }

    @Test
    void hlsMasterPlaylistInjectsSeekAndPlaylistTypeTags() throws Exception {
        Path source = Path.of(wmvFile.getFilePath());
        Path hlsDir = Path.of("./data/cache/hls", String.valueOf(wmvFile.getId()), "auto-auto");
        Files.createDirectories(hlsDir);
        Path playlist = hlsDir.resolve("master.m3u8");
        Files.writeString(
                playlist,
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\n0000.ts\n",
                StandardCharsets.UTF_8);
        Files.writeString(hlsDir.resolve("start.offset"), "120", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(playlist, FileTime.from(Instant.now().plusSeconds(5)));
        Files.setLastModifiedTime(source, FileTime.from(Instant.now()));

        mockMvc.perform(get("/api/v1/stream/" + wmvFile.getId() + "/hls/auto-auto/master.m3u8")
                        .header("Authorization", token)
                        .param("start", "120"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#EXT-X-PLAYLIST-TYPE:EVENT")))
                .andExpect(content().string(containsString("#EXT-X-START:TIME-OFFSET=120.000,PRECISE=YES")));
    }
}
