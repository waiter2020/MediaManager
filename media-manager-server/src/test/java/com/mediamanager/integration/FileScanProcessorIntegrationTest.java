package com.mediamanager.integration;

import com.mediamanager.library.dto.LibraryScanOptions;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.service.FileScanProcessor;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.service.MediaChapterService;
import com.mediamanager.media.service.MediaPostProcessQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class FileScanProcessorIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private FileScanProcessor fileScanProcessor;

    @MockBean
    private MediaChapterService mediaChapterService;

    @MockBean
    private MediaPostProcessQueueService postProcessQueueService;

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

    @Test
    void refreshMetadataOnUnchangedFileReprocessesAndQueuesPostProcess() throws Exception {
        MediaLibrary library = createLibrary("Refresh Metadata");
        Path video = tempDir.resolve("refresh-unchanged.mkv");
        Files.writeString(video, "video");
        BasicFileAttributes attrs = Files.readAttributes(video, BasicFileAttributes.class);

        MediaItem item = createItem(library, "Refresh Unchanged");
        MediaFile file = MediaFile.builder()
                .mediaItem(item)
                .filePath(normalizePath(video))
                .fileName(video.getFileName().toString())
                .fileSize(attrs.size())
                .fileModifiedAt(attrs.lastModifiedTime().toInstant())
                .deleted(false)
                .build();
        mediaFileRepository.saveAndFlush(file);

        LibraryScanOptions options = new LibraryScanOptions(true, false, true, false, "UNIDENTIFIED", false);
        FileScanProcessor.ScanResult result = fileScanProcessor.scanFile(library, video, attrs, options);

        assertThat(result.outcome()).isEqualTo(FileScanProcessor.ScanOutcome.UPDATED);
        verify(postProcessQueueService).enqueueItemFull(item.getId(), "SCAN");
    }

    @Test
    void scanMissingMetadataOnlyReprocessesIncompleteItems() throws Exception {
        MediaLibrary library = createLibrary("Missing Metadata");
        Path completeVideo = tempDir.resolve("complete.mkv");
        Path incompleteVideo = tempDir.resolve("incomplete.mkv");
        Files.writeString(completeVideo, "complete");
        Files.writeString(incompleteVideo, "incomplete");
        BasicFileAttributes completeAttrs = Files.readAttributes(completeVideo, BasicFileAttributes.class);
        BasicFileAttributes incompleteAttrs = Files.readAttributes(incompleteVideo, BasicFileAttributes.class);

        MediaItem completeItem = createItem(library, "Complete");
        completeItem.setStatus("IDENTIFIED");
        completeItem.setOverview("Already has overview");
        completeItem.setPosterPath("/posters/complete.jpg");
        mediaItemRepository.saveAndFlush(completeItem);
        MediaFile completeFile = MediaFile.builder()
                .mediaItem(completeItem)
                .filePath(normalizePath(completeVideo))
                .fileName(completeVideo.getFileName().toString())
                .fileSize(completeAttrs.size())
                .fileModifiedAt(completeAttrs.lastModifiedTime().toInstant())
                .container("mkv")
                .videoCodec("h264")
                .width(1920)
                .height(1080)
                .durationSeconds(3600)
                .deleted(false)
                .build();
        mediaFileRepository.saveAndFlush(completeFile);

        MediaItem incompleteItem = createItem(library, "Incomplete");
        incompleteItem.setStatus("UNIDENTIFIED");
        mediaItemRepository.saveAndFlush(incompleteItem);
        MediaFile incompleteFile = MediaFile.builder()
                .mediaItem(incompleteItem)
                .filePath(normalizePath(incompleteVideo))
                .fileName(incompleteVideo.getFileName().toString())
                .fileSize(incompleteAttrs.size())
                .fileModifiedAt(incompleteAttrs.lastModifiedTime().toInstant())
                .container("mkv")
                .deleted(false)
                .build();
        mediaFileRepository.saveAndFlush(incompleteFile);

        LibraryScanOptions options = new LibraryScanOptions(false, true, true, false, "UNIDENTIFIED", false);

        FileScanProcessor.ScanResult completeResult =
                fileScanProcessor.scanFile(library, completeVideo, completeAttrs, options);
        FileScanProcessor.ScanResult incompleteResult =
                fileScanProcessor.scanFile(library, incompleteVideo, incompleteAttrs, options);

        assertThat(completeResult.outcome()).isEqualTo(FileScanProcessor.ScanOutcome.UNCHANGED);
        assertThat(incompleteResult.outcome()).isEqualTo(FileScanProcessor.ScanOutcome.UPDATED);
        verify(postProcessQueueService).enqueueItemFull(incompleteItem.getId(), "SCAN");
    }

    @Test
    void skipPostProcessDoesNotQueuePostProcessForNewFile() throws Exception {
        MediaLibrary library = createLibrary("Skip Post Process");
        Path video = tempDir.resolve("new-skip-post.mkv");
        Files.writeString(video, "video");
        BasicFileAttributes attrs = Files.readAttributes(video, BasicFileAttributes.class);

        LibraryScanOptions options = new LibraryScanOptions(false, false, true, false, "UNIDENTIFIED", true);
        FileScanProcessor.ScanResult result = fileScanProcessor.scanFile(library, video, attrs, options);

        assertThat(result.outcome()).isEqualTo(FileScanProcessor.ScanOutcome.CREATED);
        verify(postProcessQueueService, never()).enqueueItemFull(anyInt(), anyString());
    }

    @Test
    void unchangedExistingVideoWithoutChaptersQueuesChapterExtraction() throws Exception {
        MediaLibrary library = createLibrary("Chapter Backfill");
        Path video = tempDir.resolve("unchanged-no-chapters.mkv");
        Files.writeString(video, "video");
        BasicFileAttributes attrs = Files.readAttributes(video, BasicFileAttributes.class);

        MediaItem item = createItem(library, "Unchanged Without Chapters");
        MediaFile file = MediaFile.builder()
                .mediaItem(item)
                .filePath(normalizePath(video))
                .fileName(video.getFileName().toString())
                .fileSize(attrs.size())
                .fileModifiedAt(attrs.lastModifiedTime().toInstant())
                .container("mkv")
                .deleted(false)
                .build();
        mediaFileRepository.saveAndFlush(file);
        when(mediaChapterService.needsChaptersForFile(sameFileId(file))).thenReturn(true);

        FileScanProcessor.ScanResult result = fileScanProcessor.scanFile(library, video, attrs);

        assertThat(result.outcome()).isEqualTo(FileScanProcessor.ScanOutcome.UNCHANGED);
        verify(postProcessQueueService).enqueueFileChapters(file.getId(), "SCAN");
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static MediaFile sameFileId(MediaFile file) {
        return argThat(candidate -> candidate != null && file.getId().equals(candidate.getId()));
    }
}
