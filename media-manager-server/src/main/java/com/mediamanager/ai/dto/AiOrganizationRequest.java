package com.mediamanager.ai.dto;

import lombok.Data;

@Data
public class AiOrganizationRequest {
    private Integer libraryId;
    private Boolean mergeDuplicateTags = true;
    private Boolean deleteUnusedTags = true;
    private Boolean deleteLowUsageTags = true;
    private Boolean protectManualTags = true;
    private Boolean recolorTags = true;
    private Boolean recolorManualTags = false;
    private Boolean createSmartCollections = true;
    private Integer lowUsageThreshold = 1;
    private Integer maxCollections = 20;
    private Integer minCollectionTagUsage = 3;
    private Integer collectionItemLimit = 50;
}
