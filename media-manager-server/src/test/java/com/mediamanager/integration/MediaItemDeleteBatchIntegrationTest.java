package com.mediamanager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MediaItemDeleteBatchIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Test
    void deleteBatchSoftDeletesItems() throws Exception {
        SysUser admin = createUser("batch-delete-admin", "ADMIN");
        String token = bearerToken(admin, Set.of("media:view", "media:delete", "library:view"));

        MediaLibrary lib = createLibrary("BatchDeleteLib");
        MediaItem item1 = createItem(lib, "Batch Item 1");
        MediaItem item2 = createItem(lib, "Batch Item 2");
        MediaFile file1 = createFile(item1, "/media/batch-delete/one.mkv");
        MediaFile file2 = createFile(item2, "/media/batch-delete/two.mkv");

        Map<String, Object> body = Map.of(
                "itemIds", List.of(item1.getId(), item2.getId()),
                "deleteSourceFile", false);

        mockMvc.perform(post("/api/v1/items/delete-batch")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.requested").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(2))
                .andExpect(jsonPath("$.data.failed").value(0));

        assertThat(mediaFileRepository.findById(file1.getId()).orElseThrow().getDeleted()).isTrue();
        assertThat(mediaFileRepository.findById(file2.getId()).orElseThrow().getDeleted()).isTrue();
    }

    @Test
    void deleteBatchRejectsEmptyItemIds() throws Exception {
        SysUser admin = createUser("batch-delete-empty", "ADMIN");
        String token = bearerToken(admin, Set.of("media:delete"));

        Map<String, Object> body = Map.of("itemIds", List.of());

        mockMvc.perform(post("/api/v1/items/delete-batch")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBatchRequiresDeleteFilePermissionForSourceDeletion() throws Exception {
        SysUser user = createUser("batch-delete-no-file", "ADMIN");
        String token = bearerToken(user, Set.of("media:delete"));

        MediaLibrary lib = createLibrary("BatchDeleteFileLib");
        MediaItem item = createItem(lib, "Source Delete Item");
        createFile(item, "/media/batch-delete/source.mkv");

        Map<String, Object> body = Map.of(
                "itemIds", List.of(item.getId()),
                "deleteSourceFile", true);

        mockMvc.perform(post("/api/v1/items/delete-batch")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }
}
