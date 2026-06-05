package com.mediamanager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.dto.MediaLibraryCreateRequest;
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

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LibraryPluginIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser admin = createUser("libadmin", "ADMIN");
        adminToken = bearerToken(admin, Set.of(
                "library:create", "library:edit", "library:view", "library:scan"));
    }

    @Test
    void createMovieLibrarySeedsPluginsWithoutExtractorsInRequest() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("Movies");
        req.setType("MOVIE");
        req.setPaths(List.of(pathReq("/media/movies")));
        req.setExtractors(null);

        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plugins").isArray())
                .andExpect(jsonPath("$.data.plugins[?(@.pluginId=='tmdb' && @.kind=='SCRAPER')]").exists())
                .andExpect(jsonPath("$.data.plugins[?(@.pluginId=='nfo')]").exists());
    }

    @Test
    void createImageLibraryUsesExifDefault() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("Photos");
        req.setType("IMAGE");
        req.setPaths(List.of(pathReq("/media/photos")));

        mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plugins[?(@.pluginId=='exif')]").exists())
                .andExpect(jsonPath("$.data.plugins[?(@.pluginId=='tmdb')]").doesNotExist());
    }

    @Test
    void applyDefaultPluginsEndpoint() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("TV");
        req.setType("TV_SHOW");
        req.setPaths(List.of(pathReq("/media/tv")));

        String createRes = mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int libraryId = objectMapper.readTree(createRes).path("data").path("id").asInt();

        mockMvc.perform(post("/api/v1/libraries/" + libraryId + "/plugins/apply-default")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.pluginId=='tmdb' && @.kind=='SCRAPER')]").exists());

        mockMvc.perform(get("/api/v1/libraries/" + libraryId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plugins[?(@.pluginId=='tmdb')]").exists());
    }

    @Test
    void getLibraryDerivesExtractorsFromPluginsNotScrapers() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("Derive");
        req.setType("MOVIE");
        req.setPaths(List.of(pathReq("/media/derive")));

        String createRes = mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int libraryId = objectMapper.readTree(createRes).path("data").path("id").asInt();

        mockMvc.perform(get("/api/v1/libraries/" + libraryId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plugins[?(@.pluginId=='tmdb' && @.kind=='SCRAPER')]").exists())
                .andExpect(jsonPath("$.data.extractors[?(@.type=='TMDB')]").doesNotExist())
                .andExpect(jsonPath("$.data.extractors[?(@.type=='NFO')]").exists());
    }

    @Test
    void putLibraryPluginsUpdatesPluginsList() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("PutPlugins");
        req.setType("IMAGE");
        req.setPaths(List.of(pathReq("/media/put-plugins")));

        String createRes = mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int libraryId = objectMapper.readTree(createRes).path("data").path("id").asInt();

        String body = """
                [{"pluginId":"exif","kind":"EXTRACTOR","enabled":true,"priority":0,"config":"{}"}]
                """;

        mockMvc.perform(put("/api/v1/libraries/" + libraryId + "/plugins")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/libraries/" + libraryId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plugins.length()").value(1))
                .andExpect(jsonPath("$.data.plugins[0].pluginId").value("exif"));
    }

    @Test
    void putLibraryPluginsNormalizesLegacyScraperRows() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("NormalizePlugins");
        req.setType("MOVIE");
        req.setPaths(List.of(pathReq("/media/normalize-plugins")));

        String createRes = mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int libraryId = objectMapper.readTree(createRes).path("data").path("id").asInt();

        String body = """
                [{"pluginId":"TMDB","kind":"EXTRACTOR","enabled":true,"priority":0,"config":"{}"}]
                """;

        mockMvc.perform(put("/api/v1/libraries/" + libraryId + "/plugins")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pluginId").value("tmdb"))
                .andExpect(jsonPath("$.data[0].kind").value("SCRAPER"));
    }

    private static MediaLibraryCreateRequest.PathReq pathReq(String path) {
        MediaLibraryCreateRequest.PathReq p = new MediaLibraryCreateRequest.PathReq();
        p.setPath(path);
        p.setPriority(0);
        return p;
    }
}
