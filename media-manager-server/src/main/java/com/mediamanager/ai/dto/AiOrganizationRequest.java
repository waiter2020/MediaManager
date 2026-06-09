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
    private Integer maxCollections = 0;
    private Integer minCollectionTagUsage = 3;
    private Integer minTagCollectionUsage = 10;
    private Integer collectionItemLimit = 0;
    private String mergeAggressiveness = "aggressive";
}
