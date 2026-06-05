package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaCollection;
import com.mediamanager.media.entity.MediaCollectionItem;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaCollectionItemRepository;
import com.mediamanager.media.repository.MediaCollectionRepository;
import com.mediamanager.system.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MediaCollectionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MediaCollectionRepository collectionRepository;
    @Autowired
    private MediaCollectionItemRepository collectionItemRepository;

    private String adminToken;
    private MediaCollection collection;

    @BeforeEach
    void setUp() {
        collectionItemRepository.deleteAll();
        collectionRepository.deleteAll();
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
        userRepository.deleteAll();

        SysUser admin = createUser("collection-admin", "SUPER_ADMIN");
        adminToken = bearerToken(admin, Set.of("media:view"));
        MediaLibrary library = createLibrary("Collection Library");
        collection = collectionRepository.save(MediaCollection.builder()
                .owner(admin)
                .name("Paged Collection")
                .type("COLLECTION")
                .visibility("PRIVATE")
                .smart(false)
                .build());

        for (int index = 1; index <= 35; index++) {
            MediaItem item = createItem(library, "Item " + index);
            collectionItemRepository.save(MediaCollectionItem.builder()
                    .collection(collection)
                    .mediaItem(item)
                    .position(index - 1)
                    .build());
        }
    }

    @Test
    void collectionItemsArePagedInCollectionOrder() throws Exception {
        mockMvc.perform(get("/api/v1/collections/{id}/items", collection.getId())
                        .param("page", "2")
                        .param("size", "10")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(35))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(4))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].title").value("Item 11"));
    }

    @Test
    void collectionSummaryOmitsItemsButKeepsCount() throws Exception {
        mockMvc.perform(get("/api/v1/collections/{id}", collection.getId())
                        .param("includeItems", "false")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(35))
                .andExpect(jsonPath("$.data.items").doesNotExist());
    }
}
