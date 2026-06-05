package com.mediamanager.search.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchReindexStatus {
    private String state;
    private String phase;
    private int ftsIndexed;
    private int embedIndexed;
    private String message;
    private long startedAt;
    private long updatedAt;

    public static SearchReindexStatus idle() {
        return SearchReindexStatus.builder()
                .state("idle")
                .phase("none")
                .ftsIndexed(0)
                .embedIndexed(0)
                .message("未在重建索引")
                .build();
    }
}
