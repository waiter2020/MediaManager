package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaChapter;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaChapterRepository;
import com.mediamanager.system.entity.SysUser;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MediaChapterIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaChapterRepository chapterRepository;

    @TempDir
    Path tempDir;

    @Test
    void detailIncludesChaptersAndThumbnailCanBeRead() throws Exception {
        SysUser admin = createUser("chapter-admin", "SUPER_ADMIN");
        String token = bearerToken(admin, Set.of("media:view", "media:play"));
        MediaLibrary library = createLibrary("Chapter Library");
        MediaItem item = createItem(library, "Chapter Movie");
        MediaFile file = createFile(item, tempDir.resolve("movie.mp4").toString());

        Path thumbnail = tempDir.resolve("chapter.jpg");
        Files.write(thumbnail, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9});
        MediaChapter chapter = chapterRepository.save(MediaChapter.builder()
                .mediaFile(file)
                .chapterIndex(0)
                .title("Opening")
                .startSeconds(12.5)
                .endSeconds(42.0)
                .source("EMBEDDED")
                .thumbnailPath(thumbnail.toString())
                .build());

        mockMvc.perform(get("/api/v1/items/{id}/detail", item.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapters[0].id").value(chapter.getId()))
                .andExpect(jsonPath("$.data.chapters[0].title").value("Opening"))
                .andExpect(jsonPath("$.data.chapters[0].startSeconds").value(12.5))
                .andExpect(jsonPath("$.data.chapters[0].thumbnailAvailable").value(true));

        mockMvc.perform(get("/api/v1/items/chapters/{id}/thumbnail", chapter.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));
    }
}
