package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.service.TmdbClientService;
import com.mediamanager.metadata.service.TvSeasonSyncService;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase2ApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TmdbClientService tmdbClientService;

    @MockBean
    private TvSeasonSyncService tvSeasonSyncService;

    private SysUser admin;
    private String adminToken;
    private MediaLibrary library;
    private MediaItem tvItem;

    @BeforeEach
    void setUp() {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        admin = createUser("admin2", "SUPER_ADMIN");
        adminToken = bearerToken(admin, Set.of(
                "library:view", "library:edit", "media:view", "media:refresh", "media:edit_metadata"));

        library = createLibrary("TV Lib");
        library.setType("TV_SHOW");
        library = libraryRepository.save(library);

        tvItem = MediaItem.builder()
                .library(library)
                .title("Test Series")
                .type("TV_SHOW")
                .status("IDENTIFIED")
                .hidden(false)
                .providerIds("{\"tmdb\":\"999\"}")
                .build();
        tvItem = mediaItemRepository.save(tvItem);

        when(tmdbClientService.resolveApiKeyForLibrary(any())).thenReturn("test-api-key");
        doNothing().when(tvSeasonSyncService).syncFromTmdb(any(), anyString(), eq("999"), anyString());
    }

    @Test
    void listPluginsReturnsRegistry() throws Exception {
        mockMvc.perform(get("/api/v1/plugins").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void syncTvSeasonsRequiresTmdbId() throws Exception {
        mockMvc.perform(post("/api/v1/items/{id}/seasons/sync", tvItem.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tmdbId").value("999"));
    }

    @Test
    void discoverEndpointReturnsRows() throws Exception {
        mockMvc.perform(get("/api/v1/discover").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }
}
