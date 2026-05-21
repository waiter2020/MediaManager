package com.mediamanager.library.service;

import com.mediamanager.library.dto.ScanProgressDTO;
import com.mediamanager.library.entity.LibraryPath;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.sync.service.SseService;
import com.mediamanager.system.dto.SystemLogEventDto;
import com.mediamanager.system.service.SystemLogBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryScanService {

    private final MediaLibraryRepository libraryRepository;
    private final FileScanProcessor fileScanProcessor;
    private final SseService sseService;

    private final Map<Integer, ScanProgressDTO> activeScans = new ConcurrentHashMap<>();

    private static final int THROTTLE_FILE_COUNT = 50;

    public Map<Integer, ScanProgressDTO> getActiveScans() {
        return activeScans;
    }

    @Async
    public void scanLibraryAsync(Integer libraryId) {
        log.info("Starting scan for library: {}", libraryId);
        sseService.broadcast("scan-start", "Started scanning library ID: " + libraryId);
        SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                .timestamp(System.currentTimeMillis())
                .level("INFO")
                .source("TASK")
                .type("SCAN_START")
                .libraryId(libraryId)
                .message("Started scanning library ID: " + libraryId)
                .build());

        MediaLibrary library = loadLibrary(libraryId);
        if (library == null) {
            log.error("Library not found for scan: {}", libraryId);
            sseService.broadcast("scan-end", "Library ID " + libraryId + " not found");
            SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                    .timestamp(System.currentTimeMillis())
                    .level("ERROR")
                    .source("TASK")
                    .type("SCAN_ERROR")
                    .libraryId(libraryId)
                    .message("Library not found for scan: " + libraryId)
                    .build());
            return;
        }

        if (library.getPaths().isEmpty()) {
            log.warn("Library '{}' has no paths configured, nothing to scan", library.getName());
            sseService.broadcast("scan-end", "Library '" + library.getName() + "' has no paths configured");
            SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                    .timestamp(System.currentTimeMillis())
                    .level("WARN")
                    .source("TASK")
                    .type("SCAN_SKIPPED")
                    .libraryId(libraryId)
                    .message("Library '" + library.getName() + "' has no paths configured, nothing to scan")
                    .build());
            return;
        }

        ScanProgressDTO progress = ScanProgressDTO.builder()
                .libraryId(libraryId)
                .libraryName(library.getName())
                .status("SCANNING")
                .currentPath("")
                .totalFiles(0)
                .scannedFiles(0)
                .matchedFiles(0)
                .newItems(0)
                .startedAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        activeScans.put(libraryId, progress);
        broadcastProgress(progress);

        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicInteger matchedFiles = new AtomicInteger(0);
        AtomicInteger newFiles = new AtomicInteger(0);

        for (LibraryPath path : library.getPaths()) {
            progress.setCurrentPath(path.getPath());
            scanDirectory(library, path.getPath(), totalFiles, matchedFiles, newFiles, progress);
        }

        updateLastScannedAt(libraryId);

        progress.setStatus("DONE");
        progress.setUpdatedAt(System.currentTimeMillis());
        broadcastProgress(progress);

        String summary = String.format(
                "Scan complete for '%s': scanned %d files, %d matched type [%s], %d new items added",
                library.getName(), totalFiles.get(), matchedFiles.get(), library.getType(), newFiles.get());
        log.info(summary);
        sseService.broadcast("scan-end", summary);
        SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                .timestamp(System.currentTimeMillis())
                .level("INFO")
                .source("TASK")
                .type("SCAN_DONE")
                .libraryId(libraryId)
                .message(summary)
                .build());

        activeScans.remove(libraryId);

        if (matchedFiles.get() == 0 && totalFiles.get() > 0) {
            String hint = String.format(
                    "Hint: Library '%s' type is [%s], but none of the %d files had a matching extension. " +
                    "Expected extensions: %s. Check if the library type is correct.",
                    library.getName(), library.getType(), totalFiles.get(),
                    fileScanProcessor.getExpectedExtensions(library.getType()));
            log.warn(hint);
            sseService.broadcast("scan-progress", hint);
            SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                    .timestamp(System.currentTimeMillis())
                    .level("WARN")
                    .source("TASK")
                    .type("SCAN_WARNING")
                    .libraryId(libraryId)
                    .message(hint)
                    .build());
        }
    }

    @Async
    public void scanSpecificDirectoryAsync(String dirPathStr) {
        Path targetDir = Paths.get(dirPathStr);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) return;

        MediaLibrary lib = findLibraryForPath(dirPathStr);
        if (lib == null) return;

        log.info("Starting targeted directory scan for path: {}", targetDir);
        sseService.broadcast("scan-progress", "Processing files in recent changed directory");
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger matched = new AtomicInteger(0);
        AtomicInteger added = new AtomicInteger(0);
        scanDirectory(lib, targetDir.toString(), total, matched, added, null);
        sseService.broadcast("scan-progress", String.format(
                "Targeted scan done: %d files, %d matched, %d new", total.get(), matched.get(), added.get()));
    }

    @Transactional(readOnly = true)
    public MediaLibrary loadLibrary(Integer libraryId) {
        return libraryRepository.findWithDetailsById(libraryId).orElse(null);
    }

    @Transactional(readOnly = true)
    public MediaLibrary findLibraryForPath(String dirPathStr) {
        return libraryRepository.findAll().stream()
                .filter(lib -> lib.getPaths().stream()
                        .anyMatch(lp -> Paths.get(dirPathStr).startsWith(Paths.get(lp.getPath()))))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void updateLastScannedAt(Integer libraryId) {
        libraryRepository.findById(libraryId).ifPresent(library -> {
            library.setLastScannedAt(Instant.now());
            libraryRepository.save(library);
        });
    }

    private void scanDirectory(MediaLibrary library, String directoryPath,
                               AtomicInteger totalFiles, AtomicInteger matchedFiles, AtomicInteger newFiles,
                               ScanProgressDTO progress) {
        Path startPath = Paths.get(directoryPath);
        if (!Files.exists(startPath) || !Files.isDirectory(startPath)) {
            log.warn("Directory does not exist or is not a directory: {}", directoryPath);
            sseService.broadcast("scan-progress", "Path not found: " + directoryPath);
            return;
        }

        log.info("Scanning directory: {}", directoryPath);
        sseService.broadcast("scan-progress", "Scanning: " + directoryPath);

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalFiles.incrementAndGet();

                    String mediaType = fileScanProcessor.checkFile(library, file);
                    if (mediaType == null) {
                        throttledProgressUpdate(progress, totalFiles, matchedFiles, newFiles, file);
                        return FileVisitResult.CONTINUE;
                    }

                    matchedFiles.incrementAndGet();

                    try {
                        fileScanProcessor.processFile(library, file, attrs, mediaType);
                        newFiles.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Failed to process file: {}", file, e);
                    }

                    throttledProgressUpdate(progress, totalFiles, matchedFiles, newFiles, file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Failed to access file: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error walking directory: {}", directoryPath, e);
            if (progress != null) {
                progress.setStatus("ERROR");
                progress.setUpdatedAt(System.currentTimeMillis());
                broadcastProgress(progress);
            }
        }
    }

    private void throttledProgressUpdate(ScanProgressDTO progress,
                                         AtomicInteger totalFiles, AtomicInteger matchedFiles,
                                         AtomicInteger newFiles, Path currentFile) {
        if (progress == null) return;
        int total = totalFiles.get();
        if (total % THROTTLE_FILE_COUNT != 0) return;

        progress.setTotalFiles(total);
        progress.setScannedFiles(total);
        progress.setMatchedFiles(matchedFiles.get());
        progress.setNewItems(newFiles.get());
        progress.setCurrentPath(currentFile.getParent() != null ? currentFile.getParent().toString() : "");
        progress.setUpdatedAt(System.currentTimeMillis());
        broadcastProgress(progress);
    }

    private void broadcastProgress(ScanProgressDTO progress) {
        sseService.broadcast("scan-status", progress);
    }
}
