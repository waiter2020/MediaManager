package com.mediamanager.library.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryScanOptionsTest {

    @Test
    void defaultsMatchCurrentScanBehavior() {
        LibraryScanOptions options = LibraryScanOptions.defaults();

        assertThat(options.refreshMetadata()).isFalse();
        assertThat(options.scanMissingMetadata()).isFalse();
        assertThat(options.reconcileMissing()).isTrue();
        assertThat(options.scrapeAfterScan()).isFalse();
        assertThat(options.scrapeTargetStatus()).isEqualTo("UNIDENTIFIED");
        assertThat(options.skipPostProcess()).isFalse();
    }

    @Test
    void fromNullRequestUsesDefaults() {
        LibraryScanOptions options = LibraryScanOptions.from(null);

        assertThat(options).isEqualTo(LibraryScanOptions.defaults());
    }

    @Test
    void fromRequestMapsExplicitValues() {
        LibraryScanRequest request = new LibraryScanRequest();
        request.setRefreshMetadata(true);
        request.setScanMissingMetadata(true);
        request.setReconcileMissing(false);
        request.setScrapeAfterScan(true);
        request.setScrapeTargetStatus("ALL");
        request.setSkipPostProcess(true);

        LibraryScanOptions options = LibraryScanOptions.from(request);

        assertThat(options.refreshMetadata()).isTrue();
        assertThat(options.scanMissingMetadata()).isTrue();
        assertThat(options.reconcileMissing()).isFalse();
        assertThat(options.scrapeAfterScan()).isTrue();
        assertThat(options.scrapeTargetStatus()).isEqualTo("ALL");
        assertThat(options.skipPostProcess()).isTrue();
    }
}
