package com.mediamanager.integration;

import com.mediamanager.ai.dto.AiOrganizationJobStatus;
import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import com.mediamanager.ai.service.AiOrganizationJobService;
import com.mediamanager.ai.service.AiOrganizationService;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaCollectionRepository;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.system.entity.SysUser;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Autowired
    private AiOrganizationService organizationService;

    @Autowired
    private MovieMetadataRepository movieMetadataRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MediaCollectionRepository collectionRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        if ("queued".equals(organizationJobService.getStatus().getState())
                || "running".equals(organizationJobService.getStatus().getState())) {
            organizationJobService.cancel();
            waitForCompletion();
        }
        libraryAccessRepository.deleteAll();
        mediaFileRepository.deleteAll();
        collectionRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
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
    void organizationKeepsEnglishTagsWhenAiProviderIsUnavailable() throws Exception {
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
        assertEquals(0, completed.getTranslatedTagCount());
        assertEquals("Action", tagRepository.findById(englishTag.getId()).orElseThrow().getName());
    }

    @Test
    void organizationMergesSynonymTagsAndKeepsMediaLinks() throws Exception {
        Tag canonicalTag = tagRepository.save(Tag.builder()
                .name("\u62a5\u9053")
                .color("#2563eb")
                .source("MANUAL")
                .build());
        Tag duplicateTag = tagRepository.save(Tag.builder()
                .name("\u5831\u5c0e")
                .color("#e11d48")
                .source("AI")
                .build());

        var library = createLibrary("ai-organizer-merge");
        var firstItem = createItem(library, "First report");
        firstItem.getTags().add(canonicalTag);
        mediaItemRepository.saveAndFlush(firstItem);
        var secondItem = createItem(library, "Second report");
        secondItem.getTags().add(duplicateTag);
        mediaItemRepository.saveAndFlush(secondItem);

        mockMvc.perform(post("/api/v1/ai/organization/apply")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mergeDuplicateTags": true,
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
        assertTrue(completed.getMergedTagCount() + completed.getTranslatedTagCount() >= 1);
        assertTrue(tagRepository.findByName("\u5831\u5c0e").isEmpty());
        Tag mergedTag = tagRepository.findByName("\u62a5\u9053").orElseThrow();
        assertEquals(2L, mediaLinkCount(mergedTag.getId()));
        assertFalse(tagRepository.existsById(duplicateTag.getId()));
    }

    @Test
    void previewIncludesStructuralSemanticMergeGroups() {
        tagRepository.save(Tag.builder()
                .name("\u9a91\u4e58")
                .color("#2563eb")
                .source("AI")
                .build());
        tagRepository.save(Tag.builder()
                .name("\u9a91\u4e58\u4f4d")
                .color("#e11d48")
                .source("MANUAL")
                .build());

        AiOrganizationRequest request = new AiOrganizationRequest();
        request.setMergeDuplicateTags(true);
        request.setMergeAggressiveness("aggressive");
        request.setDeleteUnusedTags(false);
        request.setDeleteLowUsageTags(false);
        request.setRecolorTags(false);
        request.setCreateSmartCollections(false);

        AiOrganizationResponse preview = organizationService.preview(request);

        assertTrue(preview.getSemanticMergeGroupCount() >= 1);
        assertTrue(preview.getSemanticMergeGroups().stream()
                .anyMatch(group -> "STRUCTURE".equals(group.getSource())
                        || "EXACT".equals(group.getSource())));
    }

    @Test
    void previewFindsSmartCollectionCandidatesAcrossMetadataDimensions() {
        MediaLibrary library = createLibrary("metadata-candidates");
        createMovieWithMetadata(library, "Scene 1", "Studio Alpha", "Actor One", "Action");
        createMovieWithMetadata(library, "Scene 2", "Studio Alpha", "Actor One", "Action");
        createMovieWithMetadata(library, "Scene 3", "Studio Beta", "Actor Two", "Drama");

        AiOrganizationRequest request = new AiOrganizationRequest();
        request.setLibraryId(library.getId());
        request.setCreateSmartCollections(true);
        request.setMaxCollections(10);
        request.setMinCollectionTagUsage(2);
        request.setMergeDuplicateTags(false);
        request.setDeleteUnusedTags(false);
        request.setDeleteLowUsageTags(false);
        request.setRecolorTags(false);

        AiOrganizationResponse preview = organizationService.preview(request);

        assertTrue(hasCandidate(preview, "TYPE", "MOVIE"));
        assertTrue(hasCandidate(preview, "PUBLISHER", "Studio Alpha"));
        assertTrue(hasCandidate(preview, "ACTOR", "Actor One"));
    }

    @Test
    void organizationCreatesSmartCollectionsFromMetadataCandidates() throws Exception {
        MediaLibrary library = createLibrary("metadata-collections");
        createMovieWithMetadata(library, "Scene 4", "Studio Gamma", "Actor Three", "Action");
        createMovieWithMetadata(library, "Scene 5", "Studio Gamma", "Actor Three", "Action");

        mockMvc.perform(post("/api/v1/ai/organization/apply")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "libraryId": %d,
                                  "mergeDuplicateTags": false,
                                  "deleteUnusedTags": false,
                                  "deleteLowUsageTags": false,
                                  "recolorTags": false,
                                  "createSmartCollections": true,
                                  "maxCollections": 10,
                                  "minCollectionTagUsage": 2,
                                  "collectionItemLimit": 50
                                }
                                """.formatted(library.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        AiOrganizationJobStatus completed = waitForCompletion();
        assertEquals("done", completed.getState());
        assertTrue(completed.getCreatedCollectionCount() >= 3);
        assertTrue(completed.getResult().getGeneratedCollections().stream()
                .anyMatch(collection -> "PUBLISHER".equals(collection.getDimension())
                        && "Studio Gamma".equals(collection.getDisplayValue())
                        && Boolean.TRUE.equals(collection.getCreated())));
        assertTrue(completed.getResult().getGeneratedCollections().stream()
                .anyMatch(collection -> "ACTOR".equals(collection.getDimension())
                        && "Actor Three".equals(collection.getDisplayValue())
                        && Boolean.TRUE.equals(collection.getCreated())));
    }

    @Test
    void previewRanksMetadataCandidatesAheadOfTagCandidatesWithEqualUsage() {
        MediaLibrary library = createLibrary("ranked-candidates");
        Tag sharedTag = tagRepository.save(Tag.builder()
                .name("sharedtag")
                .color("#8b5cf6")
                .source("AI")
                .build());
        MediaItem first = createMovieWithMetadata(library, "Rank 1", "Studio Rank", "Shared Actor", "Action");
        MediaItem second = createMovieWithMetadata(library, "Rank 2", "Studio Rank", "Shared Actor", "Action");
        first.getTags().add(sharedTag);
        second.getTags().add(sharedTag);
        mediaItemRepository.saveAndFlush(first);
        mediaItemRepository.saveAndFlush(second);

        AiOrganizationRequest request = new AiOrganizationRequest();
        request.setLibraryId(library.getId());
        request.setCreateSmartCollections(true);
        request.setMaxCollections(0);
        request.setMinCollectionTagUsage(2);
        request.setMinTagCollectionUsage(2);
        request.setMergeDuplicateTags(false);
        request.setDeleteUnusedTags(false);
        request.setDeleteLowUsageTags(false);
        request.setRecolorTags(false);

        List<AiOrganizationResponse.SmartCollectionCandidate> candidates =
                organizationService.preview(request).getSmartCollectionCandidates();
        int actorIndex = candidateIndex(candidates, "ACTOR", "Shared Actor");
        int tagIndex = candidateIndex(candidates, "TAG", "sharedtag");

        assertTrue(actorIndex >= 0);
        assertTrue(tagIndex >= 0);
        assertTrue(actorIndex < tagIndex);
    }

    @Test
    void previewExcludesCleanupTagsFromSmartCollectionCandidates() {
        MediaLibrary library = createLibrary("cleanup-candidates");
        Tag cleanupTag = tagRepository.save(Tag.builder()
                .name("lowusage")
                .color("#8b5cf6")
                .source("AI")
                .build());
        MediaItem item = createMovieWithMetadata(library, "Cleanup 1", "Studio Cleanup", "Actor Cleanup", "Drama");
        item.getTags().add(cleanupTag);
        mediaItemRepository.saveAndFlush(item);

        AiOrganizationRequest request = new AiOrganizationRequest();
        request.setLibraryId(library.getId());
        request.setCreateSmartCollections(true);
        request.setMaxCollections(0);
        request.setMinCollectionTagUsage(1);
        request.setMinTagCollectionUsage(1);
        request.setDeleteUnusedTags(false);
        request.setDeleteLowUsageTags(true);
        request.setLowUsageThreshold(5);
        request.setProtectManualTags(false);
        request.setMergeDuplicateTags(false);
        request.setRecolorTags(false);

        AiOrganizationResponse preview = organizationService.preview(request);

        assertFalse(hasCandidate(preview, "TAG", "lowusage"));
    }

    @Test
    void organizationCreatesUnlimitedSmartCollectionsWithFullItemCounts() throws Exception {
        MediaLibrary library = createLibrary("unlimited-collections");
        createMovieWithMetadata(library, "Unlimited 1", "Studio Unlimited", "Actor Unlimited", "Action");
        createMovieWithMetadata(library, "Unlimited 2", "Studio Unlimited", "Actor Unlimited", "Action");

        mockMvc.perform(post("/api/v1/ai/organization/apply")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "libraryId": %d,
                                  "mergeDuplicateTags": false,
                                  "deleteUnusedTags": false,
                                  "deleteLowUsageTags": false,
                                  "recolorTags": false,
                                  "createSmartCollections": true,
                                  "maxCollections": 1,
                                  "minCollectionTagUsage": 2,
                                  "collectionItemLimit": 0
                                }
                                """.formatted(library.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true));

        AiOrganizationJobStatus completed = waitForCompletion();
        assertEquals("done", completed.getState());
        assertEquals(1, completed.getCreatedCollectionCount());

        mockMvc.perform(get("/api/v1/collections")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemCount").value(2))
                .andExpect(jsonPath("$.data[0].rule.limit").value(0));

        assertTrue(collectionRepository.findAll().stream()
                .anyMatch(collection -> collection.getRuleJson() != null
                        && collection.getRuleJson().contains("\"limit\":0")));
    }

    private int candidateIndex(
            List<AiOrganizationResponse.SmartCollectionCandidate> candidates,
            String dimension,
            String value) {
        for (int index = 0; index < candidates.size(); index++) {
            AiOrganizationResponse.SmartCollectionCandidate candidate = candidates.get(index);
            if (dimension.equals(candidate.getDimension()) && value.equals(candidate.getValue())) {
                return index;
            }
        }
        return -1;
    }

    private MediaItem createMovieWithMetadata(
            MediaLibrary library,
            String title,
            String studio,
            String actor,
            String genre) {
        MediaItem item = createItem(library, title);
        movieMetadataRepository.save(MovieMetadata.builder()
                .mediaItem(item)
                .genres("[\"" + genre + "\"]")
                .studios("[\"" + studio + "\"]")
                .castInfo("[{\"name\":\"" + actor + "\"}]")
                .build());
        return item;
    }

    private boolean hasCandidate(AiOrganizationResponse preview, String dimension, String value) {
        return preview.getSmartCollectionCandidates().stream()
                .anyMatch(candidate -> dimension.equals(candidate.getDimension())
                        && value.equals(candidate.getValue()));
    }

    private long mediaLinkCount(Integer tagId) {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM media_item_tag WHERE tag_id = :tagId")
                .setParameter("tagId", tagId)
                .getSingleResult())
                .longValue();
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
