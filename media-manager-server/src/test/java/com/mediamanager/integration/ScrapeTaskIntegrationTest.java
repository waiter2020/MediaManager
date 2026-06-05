package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaItem;
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
class ScrapeTaskIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private MediaLibrary library;

    @BeforeEach
    void setUp() {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser admin = createUser("scraper", "SUPER_ADMIN");
        token = bearerToken(admin, Set.of("library:edit", "task:view", "library:view"));
        library = createLibrary("ScrapeLib");
        MediaItem pending = createItem(library, "Unidentified Movie");
        pending.setStatus("UNIDENTIFIED");
        mediaItemRepository.save(pending);
    }

    @Test
    void startScrapeAllEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/scrape/start")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"UNIDENTIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists());
    }

    @Test
    void createAndListScrapeTask() throws Exception {
        String body = "{\"libraryId\":" + library.getId() + ",\"targetStatus\":\"UNIDENTIFIED\"}";

        mockMvc.perform(post("/api/v1/scrape/tasks")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").exists());

        mockMvc.perform(get("/api/v1/scrape/tasks")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void previewScrapeTaskExplainsCandidates() throws Exception {
        String body = "{\"libraryId\":" + library.getId() + ",\"targetStatus\":\"UNIDENTIFIED\",\"mediaTypes\":[\"MOVIE\"]}";

        mockMvc.perform(post("/api/v1/scrape/tasks/preview")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.byStatus.UNIDENTIFIED").value(1))
                .andExpect(jsonPath("$.data.byType.MOVIE").value(1));
    }
}
