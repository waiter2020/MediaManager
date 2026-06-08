package com.mediamanager.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOrganizationResponse {
    private Integer libraryId;
    private Boolean applied;
    private Integer unusedTagCount;
    private Integer cleanupTagCount;
    private Integer duplicateGroupCount;
    private Integer smartCollectionCandidateCount;
    private Integer deletedUnusedTagCount;
    private Integer deletedCleanupTagCount;
    private Integer mergedTagCount;
    private Integer translatedTagCount;
    private Integer recoloredTagCount;
    private Integer createdCollectionCount;
    private List<TagUsage> unusedTags;
    private List<TagUsage> cleanupTags;
    private List<DuplicateTagGroup> duplicateTagGroups;
    private List<SmartCollectionCandidate> smartCollectionCandidates;
    private List<GeneratedCollection> generatedCollections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagUsage {
        private Integer id;
        private String name;
        private String color;
        private String source;
        private Long usageCount;
        private String cleanupReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DuplicateTagGroup {
        private String semanticKey;
        private TagUsage canonicalTag;
        private List<TagUsage> duplicateTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmartCollectionCandidate {
        private String key;
        private String dimension;
        private String dimensionLabel;
        private String name;
        private String value;
        private String displayValue;
        private String color;
        private String source;
        private Long usageCount;
        private Integer tagId;
        private String tagName;
        private Integer categoryId;
        private String categoryName;
        private String metadataField;
        private String metadataValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedCollection {
        private Integer id;
        private String name;
        private String dimension;
        private String dimensionLabel;
        private String value;
        private String displayValue;
        private Integer tagId;
        private String tagName;
        private Integer categoryId;
        private String categoryName;
        private String metadataField;
        private String metadataValue;
        private Long itemCount;
        private Boolean created;
    }
}
