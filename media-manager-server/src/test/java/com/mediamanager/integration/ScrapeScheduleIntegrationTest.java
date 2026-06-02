package com.mediamanager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.dto.MediaLibraryCreateRequest;
import com.mediamanager.metadata.dto.ScrapeScheduleDto;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScrapeScheduleIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private int movieLibraryId;

    @BeforeEach
    void setUp() throws Exception {
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser admin = createUser("schedadmin", "SUPER_ADMIN");
        adminToken = bearerToken(admin, Set.of(
                "library:create", "library:edit", "library:view", "task:view", "task:execute"));

        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("SchedMovies");
        req.setType("MOVIE");
        req.setPaths(List.of(pathReq("/media/sched")));

        String createRes = mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        movieLibraryId = objectMapper.readTree(createRes).path("data").path("id").asInt();
    }

    @Test
    void createLibraryScheduleRequiresEnabledScraper() throws Exception {
        ScrapeScheduleDto dto = ScrapeScheduleDto.builder()
                .name("Nightly")
                .enabled(true)
                .scheduleType("CRON")
                .cronExpr("0 0 4 * * ?")
                .scope("LIBRARY")
                .libraryId(movieLibraryId)
                .targetStatus("UNIDENTIFIED")
                .maxConcurrency(1)
                .build();

        mockMvc.perform(post("/api/v1/scrape/schedules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.libraryId").value(movieLibraryId));
    }

    @Test
    void rejectLibraryScheduleWithoutScraperPlugins() throws Exception {
        MediaLibraryCreateRequest req = new MediaLibraryCreateRequest();
        req.setName("NoScraper");
        req.setType("IMAGE");
        req.setPaths(List.of(pathReq("/media/img")));

        String createRes = mockMvc.perform(post("/api/v1/libraries")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int imageLibId = objectMapper.readTree(createRes).path("data").path("id").asInt();

        ScrapeScheduleDto dto = ScrapeScheduleDto.builder()
                .name("Bad")
                .enabled(true)
                .scheduleType("FIXED_DELAY")
                .intervalSeconds(3600)
                .scope("LIBRARY")
                .libraryId(imageLibId)
                .targetStatus("UNIDENTIFIED")
                .maxConcurrency(1)
                .build();

        mockMvc.perform(post("/api/v1/scrape/schedules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectInvalidCron() throws Exception {
        ScrapeScheduleDto dto = ScrapeScheduleDto.builder()
                .name("BadCron")
                .enabled(true)
                .scheduleType("CRON")
                .cronExpr("not a cron")
                .scope("GLOBAL")
                .targetStatus("UNIDENTIFIED")
                .maxConcurrency(1)
                .build();

        mockMvc.perform(post("/api/v1/scrape/schedules")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private static MediaLibraryCreateRequest.PathReq pathReq(String path) {
        MediaLibraryCreateRequest.PathReq p = new MediaLibraryCreateRequest.PathReq();
        p.setPath(path);
        p.setPriority(0);
        return p;
    }
}
