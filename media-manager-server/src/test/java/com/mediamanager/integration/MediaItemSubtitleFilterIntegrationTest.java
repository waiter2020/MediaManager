package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.entity.MediaSubtitle;
import com.mediamanager.media.repository.MediaSubtitleRepository;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MediaItemSubtitleFilterIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaSubtitleRepository subtitleRepository;

    private String adminToken;
    private MediaItem withSubtitle;
    private MediaItem withoutSubtitle;

    @BeforeEach
    void setUp() {
        subtitleRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser admin = createUser("subtitle-filter-admin", "ADMIN");
        adminToken = bearerToken(admin, Set.of("media:view"));

        MediaLibrary library = createLibrary("SubtitleFilterLib");
        withSubtitle = createItem(library, "Movie With Subtitle");
        withoutSubtitle = createItem(library, "Movie Without Subtitle");

        subtitleRepository.save(MediaSubtitle.builder()
                .mediaItem(withSubtitle)
                .filePath("/media/subtitles/with-sub.vtt")
                .fileName("with-sub.vtt")
                .language("zh")
                .format("vtt")
                .source("LOCAL")
                .build());
    }

    @Test
    void getItemsWithHasSubtitleTrueReturnsOnlyItemsWithSubtitles() throws Exception {
        mockMvc.perform(get("/api/v1/items")
                        .header("Authorization", adminToken)
                        .param("hasSubtitle", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(withSubtitle.getId()));
    }

    @Test
    void getItemsWithHasSubtitleFalseReturnsOnlyItemsWithoutSubtitles() throws Exception {
        mockMvc.perform(get("/api/v1/items")
                        .header("Authorization", adminToken)
                        .param("hasSubtitle", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(withoutSubtitle.getId()));
    }

    @Test
    void unifiedSearchWithHasSubtitleFiltersResults() throws Exception {
        String body = "{\"query\":\"Movie\",\"hasSubtitle\":true,\"page\":1,\"size\":20}";
        mockMvc.perform(post("/api/v1/search/unified")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.results.total").value(1))
                .andExpect(jsonPath("$.data.results.items[0].id").value(withSubtitle.getId()));
    }
}
