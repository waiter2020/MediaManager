package com.mediamanager.library.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanProgressDTO {
    private Integer libraryId;
    private String libraryName;
    private String status;
    private String currentPath;
    private int totalFiles;
    private int scannedFiles;
    private int matchedFiles;
    private int skippedFiles;
    private int newItems;
    private int updatedItems;
    private int restoredItems;
    private int failedItems;
    private int missingItems;
    private long startedAt;
    private long updatedAt;

    @Builder.Default
    private List<ScanErrorDTO> recentErrors = new CopyOnWriteArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanErrorDTO {
        private String path;
        private String message;
        private long timestamp;
    }
}
