package com.mediamanager.integration;

import com.mediamanager.ai.dto.AiOrganizationJobStatus;
import com.mediamanager.ai.service.AiOrganizationJobService;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiOrganizationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private AiOrganizationJobService organizationJobService;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        if ("queued".equals(organizationJobService.getStatus().getState())
                || "running".equals(organizationJobService.getStatus().getState())) {
            organizationJobService.cancel();
            waitForCompletion();
        }
        tagRepository.deleteAll();
        userRepository.deleteAll();
        SysUser admin = createUser("ai-organizer", "SUPER_ADMIN");
        adminToken = bearerToken(admin, Set.of("tag:manage", "media:edit_metadata"));
    }

    @Test
    void organizationRunsInBackgroundAndRemovesBadTag() throws Exception {
        Tag badTag = tagRepository.save(Tag.builder()
                .name("I'm sorry, here are appropriate tags")
                .color("#8b5cf6")
                .source("AI")
                .build());

        Instant started = Instant.now();
        mockMvc.perform(post("/api/v1/ai/organization/apply")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mergeDuplicateTags": false,
                                  "deleteUnusedTags": true,
                                  "deleteLowUsageTags": true,
                                  "recolorTags": false,
                                  "createSmartCollections": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        assertTrue(Duration.between(started, Instant.now()).toMillis() < 2000);
        AiOrganizationJobStatus completed = waitForCompletion();
        assertEquals("done", completed.getState());
        assertTrue(completed.getDeletedCleanupTagCount() >= 1);
        assertFalse(tagRepository.existsById(badTag.getId()));
    }

    @Test
    void organizationTranslatesKnownEnglishTagsToChinese() throws Exception {
        Tag englishTag = tagRepository.save(Tag.builder()
                .name("Action")
                .color("#8b5cf6")
                .source("MANUAL")
                .build());

        mockMvc.perform(post("/api/v1/ai/organization/apply")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mergeDuplicateTags": false,
                                  "deleteUnusedTags": false,
                                  "deleteLowUsageTags": false,
                                  "recolorTags": false,
                                  "createSmartCollections": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        AiOrganizationJobStatus completed = waitForCompletion();
        assertEquals("done", completed.getState());
        assertEquals(1, completed.getTranslatedTagCount());
        assertEquals("\u52a8\u4f5c", tagRepository.findById(englishTag.getId()).orElseThrow().getName());
        assertTrue(tagRepository.findByName("Action").isEmpty());
    }

    private AiOrganizationJobStatus waitForCompletion() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        AiOrganizationJobStatus current = organizationJobService.getStatus();
        while (System.currentTimeMillis() < deadline
                && ("queued".equals(current.getState()) || "running".equals(current.getState()))) {
            Thread.sleep(50);
            current = organizationJobService.getStatus();
        }
        return current;
    }
}
