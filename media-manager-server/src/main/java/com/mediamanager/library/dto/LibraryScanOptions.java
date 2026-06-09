package com.mediamanager.library.dto;

public record LibraryScanOptions(
        boolean refreshMetadata,
        boolean scanMissingMetadata,
        boolean reconcileMissing,
        boolean scrapeAfterScan,
        String scrapeTargetStatus,
        boolean skipPostProcess) {

    public static LibraryScanOptions defaults() {
        return new LibraryScanOptions(false, false, true, false, "UNIDENTIFIED", false);
    }

    public static LibraryScanOptions from(LibraryScanRequest request) {
        if (request == null) {
            return defaults();
        }
        return new LibraryScanOptions(
                Boolean.TRUE.equals(request.getRefreshMetadata()),
                Boolean.TRUE.equals(request.getScanMissingMetadata()),
                request.getReconcileMissing() == null || Boolean.TRUE.equals(request.getReconcileMissing()),
                Boolean.TRUE.equals(request.getScrapeAfterScan()),
                request.getScrapeTargetStatus() != null ? request.getScrapeTargetStatus() : "UNIDENTIFIED",
                Boolean.TRUE.equals(request.getSkipPostProcess()));
    }
}
