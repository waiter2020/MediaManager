package com.mediamanager.library.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private int newItems;
    private long startedAt;
    private long updatedAt;
}
