package com.mediamanager.integration;

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
class SearchNlIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        SysUser admin = createUser("search-admin", "ADMIN");
        adminToken = bearerToken(admin, Set.of("media:view", "system:manage"));
    }

    @Test
    void naturalLanguageSearchReturnsSuccess() throws Exception {
        String body = "{\"query\":\"2020 action movies\",\"libraryId\":null,\"page\":1,\"size\":10}";
        mockMvc.perform(post("/api/v1/search/query")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void unifiedSearchReturnsSuccess() throws Exception {
        String body = "{\"query\":\"2020 action movies\",\"libraryId\":null,\"page\":1,\"size\":10}";
        mockMvc.perform(post("/api/v1/search/unified")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.results").exists());
    }

    @Test
    void aiHealthReturnsProviderInTestProfile() throws Exception {
        mockMvc.perform(get("/api/v1/ai/health")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.provider").exists());
    }
}
