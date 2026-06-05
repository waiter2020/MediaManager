package com.mediamanager.ai.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class AiOrganizationJobStatus {
    String state;
    String phase;
    Integer libraryId;
    int total;
    int processed;
    int failed;
    int mergedTagCount;
    int deletedCleanupTagCount;
    int deletedUnusedTagCount;
    int translatedTagCount;
    int recoloredTagCount;
    int createdCollectionCount;
    boolean cancelRequested;
    Long startedAt;
    Long finishedAt;
    String message;
    AiOrganizationResponse result;

    public static AiOrganizationJobStatus idle() {
        return AiOrganizationJobStatus.builder()
                .state("idle")
                .phase("idle")
                .message("暂无整理任务")
                .build();
    }
}
