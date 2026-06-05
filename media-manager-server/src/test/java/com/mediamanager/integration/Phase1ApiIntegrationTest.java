package com.mediamanager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.dto.IdentifyRequest;
import com.mediamanager.metadata.service.TmdbClientService;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Phase1ApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TmdbClientService tmdbClientService;

    private SysUser restrictedUser;
    private MediaItem forbiddenItem;
    private MediaFile forbiddenFile;
    private String restrictedToken;

    @BeforeEach
    void setUp() {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        MediaLibrary allowedLib = createLibrary("Allowed");
        MediaLibrary forbiddenLib = createLibrary("Forbidden");

        restrictedUser = createUser("viewer", "USER");
        grantLibraryView(restrictedUser, allowedLib.getId());

        MediaItem allowedItem = createItem(allowedLib, "Allowed Movie");
        forbiddenItem = createItem(forbiddenLib, "Forbidden Movie");
        forbiddenFile = createFile(forbiddenItem, "/media/forbidden/test.mkv");

        restrictedToken = bearerToken(restrictedUser, Set.of("media:view", "media:play"));
    }

    @Test
    void setupStatusReflectsUserCount() throws Exception {
        mockMvc.perform(get("/api/v1/auth/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.setupCompleted").value(true));
    }

    @Test
    void userCannotAccessItemInUnauthorizedLibrary() throws Exception {
        mockMvc.perform(get("/api/v1/items/" + forbiddenItem.getId())
                        .header("Authorization", restrictedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void userCannotStreamFileInUnauthorizedLibrary() throws Exception {
        mockMvc.perform(get("/api/v1/stream/" + forbiddenFile.getId())
                        .header("Authorization", restrictedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void restrictedUserSemanticSearchReturnsEmptyWithoutLibraryAccess() throws Exception {
        String body = "{\"query\":\"action movie\",\"libraryId\":null,\"limit\":10}";
        mockMvc.perform(post("/api/v1/search/semantic")
                        .header("Authorization", restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void userCannotRecordPlaybackInUnauthorizedLibrary() throws Exception {
        String body = "{\"mediaItemId\":" + forbiddenItem.getId() + ",\"position\":0}";
        mockMvc.perform(post("/api/v1/user/play")
                        .header("Authorization", restrictedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40302));
    }

    @Test
    void identifyWithTmdbUpdatesItem() throws Exception {
        SysUser editor = createUser("editor", "ADMIN");
        String token = bearerToken(editor, Set.of("media:edit_metadata", "media:view"));

        MediaLibrary lib = createLibrary("IdentifyLib");
        MediaItem item = createItem(lib, "Inception");

        when(tmdbClientService.resolveApiKeyForLibrary(any())).thenReturn("test-key");
        when(tmdbClientService.fetchByExternalId(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(MetadataResult.builder()
                        .title("Inception")
                        .overview("Dream within dream")
                        .build());

        IdentifyRequest body = new IdentifyRequest();
        body.setProvider("tmdb");
        body.setExternalId("27205");

        mockMvc.perform(post("/api/v1/items/" + item.getId() + "/identify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/items/" + item.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Inception"));
    }

    @Test
    void recycleBinListsAndRestoresSoftDeletedItem() throws Exception {
        SysUser admin = createUser("admin", "ADMIN");
        String token = bearerToken(admin, Set.of(
                "media:view", "media:delete", "media:delete_file", "library:view"));

        MediaLibrary lib = createLibrary("RecycleLib");
        MediaItem item = createItem(lib, "To Delete");
        MediaFile file = createFile(item, "/media/recycle/test.mkv");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/items/" + item.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/recycle-bin")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(file.getId()));

        mockMvc.perform(post("/api/v1/recycle-bin/" + file.getId() + "/restore")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void meEndpointIncludesPermissions() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", restrictedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions[0]").exists());
    }
}
