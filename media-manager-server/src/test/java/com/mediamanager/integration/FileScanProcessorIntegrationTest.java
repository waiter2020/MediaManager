package com.mediamanager.integration;

import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.service.FileScanProcessor;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FileScanProcessorIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FileScanProcessor fileScanProcessor;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        mediaFileRepository.deleteAll();
        mediaItemRepository.deleteAll();
        libraryRepository.deleteAll();
    }

    @Test
    void unchangedExistingFileUpdatesLastScannedInsideTransaction() throws Exception {
        MediaLibrary library = createLibrary("Lazy Scan");
        Path video = tempDir.resolve("unchanged.mkv");
        Files.writeString(video, "video");
        BasicFileAttributes attrs = Files.readAttributes(video, BasicFileAttributes.class);

        MediaItem item = createItem(library, "Unchanged");
        MediaFile file = MediaFile.builder()
                .mediaItem(item)
                .filePath(normalizePath(video))
                .fileName(video.getFileName().toString())
                .fileSize(attrs.size())
                .fileModifiedAt(attrs.lastModifiedTime().toInstant())
                .deleted(false)
                .build();
        mediaFileRepository.saveAndFlush(file);

        FileScanProcessor.ScanResult result = fileScanProcessor.scanFile(library, video, attrs);

        assertThat(result.outcome()).isEqualTo(FileScanProcessor.ScanOutcome.UNCHANGED);
        assertThat(result.errorMessage()).isNull();
        assertThat(mediaItemRepository.findById(item.getId()).orElseThrow().getLastScannedAt()).isNotNull();
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
