package com.mediamanager.integration;

import com.mediamanager.system.dto.SecuritySettingsUpdateRequest;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.service.SysConfigService;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SystemSettingsIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SysConfigService sysConfigService;

    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        SysUser admin = createUser("settingsadmin", "ADMIN");
        adminToken = bearerToken(admin, Set.of("system:manage"));
    }

    @AfterEach
    void restoreAuthSettingCache() {
        SecuritySettingsUpdateRequest request = new SecuritySettingsUpdateRequest();
        request.setAuthEnabled(true);
        sysConfigService.updateSecuritySettings(request);
    }

    @Test
    void securitySettingsReportSavedAndEffectiveAuthStates() throws Exception {
        mockMvc.perform(get("/api/v1/system/settings/security")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authEnabled").value(true))
                .andExpect(jsonPath("$.data.effectiveAuthEnabled").value(true))
                .andExpect(jsonPath("$.data.requiresRestart").value(true))
                .andExpect(jsonPath("$.data.restartRequired").value(false));

        mockMvc.perform(put("/api/v1/system/settings/security")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authEnabled").value(false))
                .andExpect(jsonPath("$.data.effectiveAuthEnabled").value(true))
                .andExpect(jsonPath("$.data.requiresRestart").value(true))
                .andExpect(jsonPath("$.data.restartRequired").value(true));
    }

    @Test
    void mediaProcessingRoundTrip() throws Exception {
        String body = "{\"ffmpegPath\":\"/usr/bin/ffmpeg-test\",\"ffprobePath\":\"/usr/bin/ffprobe-test\"}";
        mockMvc.perform(put("/api/v1/system/settings/media-processing")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ffmpegPath").value("/usr/bin/ffmpeg-test"))
                .andExpect(jsonPath("$.data.ffprobePath").value("/usr/bin/ffprobe-test"));

        mockMvc.perform(get("/api/v1/system/settings/media-processing")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ffmpegPath").value("/usr/bin/ffmpeg-test"));
    }

    @Test
    void integrationsMasksTmdbKey() throws Exception {
        mockMvc.perform(put("/api/v1/system/settings/integrations")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbApiKey\":\"secret-key-123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system/settings/integrations")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tmdbApiKey").value("***"))
                .andExpect(jsonPath("$.data.tmdbApiKeyConfigured").value(true));
    }

    @Test
    void legacyConfigRejectsAiKeys() throws Exception {
        mockMvc.perform(put("/api/v1/system/config")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ai.default_provider\":\"noop\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void aiConfigRoundTrip() throws Exception {
        String updateBody = "{"
                + "\"defaultProvider\":\"openai-compatible\","
                + "\"openaiBaseUrl\":\"https://api.openai-test.com/v1\","
                + "\"openaiApiKey\":\"my-secret-key\","
                + "\"llmModel\":\"gpt-4o-mini\","
                + "\"embedModel\":\"text-embedding-3-small\","
                + "\"classifierEnabled\":false,"
                + "\"outboundAllowed\":true,"
                + "\"timeoutMs\":20000"
                + "}";

        mockMvc.perform(put("/api/v1/ai/config")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultProvider").value("openai-compatible"))
                .andExpect(jsonPath("$.data.openaiBaseUrl").value("https://api.openai-test.com/v1"))
                .andExpect(jsonPath("$.data.openaiApiKey").value("***"))
                .andExpect(jsonPath("$.data.llmModel").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data.embedModel").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.data.classifierEnabled").value(false))
                .andExpect(jsonPath("$.data.outboundAllowed").value(true))
                .andExpect(jsonPath("$.data.timeoutMs").value(20000));

        mockMvc.perform(get("/api/v1/ai/config")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultProvider").value("openai-compatible"))
                .andExpect(jsonPath("$.data.openaiApiKey").value("***"))
                .andExpect(jsonPath("$.data.llmModel").value("gpt-4o-mini"));
    }

    @Test
    void publicStatusIncludesTheme() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme").exists());
    }
}
