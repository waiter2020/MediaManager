package com.mediamanager.library.service;

import com.mediamanager.library.entity.LibraryPath;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.sync.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryScanService {

    private final MediaLibraryRepository libraryRepository;
    private final FileScanProcessor fileScanProcessor;
    private final SseService sseService;

    /**
     * Asynchronously scan a library. No @Transactional here — each file is processed
     * in its own independent transaction via FileScanProcessor.
     */
    @Async
    public void scanLibraryAsync(Integer libraryId) {
        log.info("Starting scan for library: {}", libraryId);
        sseService.broadcast("scan-start", "Started scanning library ID: " + libraryId);

        MediaLibrary library = loadLibrary(libraryId);
        if (library == null) {
            log.error("Library not found for scan: {}", libraryId);
            sseService.broadcast("scan-end", "Library ID " + libraryId + " not found");
            return;
        }

        if (library.getPaths().isEmpty()) {
            log.warn("Library '{}' has no paths configured, nothing to scan", library.getName());
            sseService.broadcast("scan-end", "Library '" + library.getName() + "' has no paths configured");
            return;
        }

        AtomicInteger totalFiles = new AtomicInteger(0);
        AtomicInteger matchedFiles = new AtomicInteger(0);
        AtomicInteger newFiles = new AtomicInteger(0);

        for (LibraryPath path : library.getPaths()) {
            scanDirectory(library, path.getPath(), totalFiles, matchedFiles, newFiles);
        }

        updateLastScannedAt(libraryId);

        String summary = String.format(
                "Scan complete for '%s': scanned %d files, %d matched type [%s], %d new items added",
                library.getName(), totalFiles.get(), matchedFiles.get(), library.getType(), newFiles.get());
        log.info(summary);
        sseService.broadcast("scan-end", summary);

        if (matchedFiles.get() == 0 && totalFiles.get() > 0) {
            String hint = String.format(
                    "Hint: Library '%s' type is [%s], but none of the %d files had a matching extension. " +
                    "Expected extensions: %s. Check if the library type is correct.",
                    library.getName(), library.getType(), totalFiles.get(),
                    fileScanProcessor.getExpectedExtensions(library.getType()));
            log.warn(hint);
            sseService.broadcast("scan-progress", hint);
        }
    }

    @Async
    public void scanSpecificDirectoryAsync(String dirPathStr) {
        Path targetDir = Paths.get(dirPathStr);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) return;

        // Load libraries in a read-only transaction
        MediaLibrary lib = findLibraryForPath(dirPathStr);
        if (lib == null) return;

        log.info("Starting targeted directory scan for path: {}", targetDir);
        sseService.broadcast("scan-progress", "Processing files in recent changed directory");
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger matched = new AtomicInteger(0);
        AtomicInteger added = new AtomicInteger(0);
        scanDirectory(lib, targetDir.toString(), total, matched, added);
        sseService.broadcast("scan-progress", String.format(
                "Targeted scan done: %d files, %d matched, %d new", total.get(), matched.get(), added.get()));
    }

    /**
     * Load library with paths and extractor configs in a short read-only transaction.
     */
    @Transactional(readOnly = true)
    public MediaLibrary loadLibrary(Integer libraryId) {
        return libraryRepository.findWithDetailsById(libraryId).orElse(null);
    }

    /**
     * Find which library contains the given directory path.
     */
    @Transactional(readOnly = true)
    public MediaLibrary findLibraryForPath(String dirPathStr) {
        return libraryRepository.findAll().stream()
                .filter(lib -> lib.getPaths().stream()
                        .anyMatch(lp -> Paths.get(dirPathStr).startsWith(Paths.get(lp.getPath()))))
                .findFirst()
                .orElse(null);
    }

    /**
     * Update the last scanned timestamp in its own transaction.
     */
    @Transactional
    public void updateLastScannedAt(Integer libraryId) {
        libraryRepository.findById(libraryId).ifPresent(library -> {
            library.setLastScannedAt(Instant.now());
            libraryRepository.save(library);
        });
    }

    private void scanDirectory(MediaLibrary library, String directoryPath,
                               AtomicInteger totalFiles, AtomicInteger matchedFiles, AtomicInteger newFiles) {
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
                    if (mediaType == null) return FileVisitResult.CONTINUE;

                    matchedFiles.incrementAndGet();

                    try {
                        fileScanProcessor.processFile(library, file, attrs, mediaType);
                        newFiles.incrementAndGet();
                    } catch (Exception e) {
                        log.error("Failed to process file: {}", file, e);
                    }

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
        }
    }
}
