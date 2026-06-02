package com.mediamanager.library.service;

import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.library.dto.ScanProgressDTO;
import com.mediamanager.library.entity.LibraryPath;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.service.MediaFileLifecycleService;
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
import java.util.LinkedHashSet;
import java.util.List;
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
    private final MediaFileRepository mediaFileRepository;
    private final MediaFileLifecycleService mediaFileLifecycleService;
    private final StoragePathMapper storagePathMapper;

    private final Map<Integer, ScanProgressDTO> activeScans = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> cancelledScans = new ConcurrentHashMap<>();

    private static final int THROTTLE_FILE_COUNT = 50;

    public Map<Integer, ScanProgressDTO> getActiveScans() {
        return activeScans;
    }

    public void cancelScan(Integer libraryId) {
        if (activeScans.containsKey(libraryId)) {
            cancelledScans.put(libraryId, true);
            log.info("Requested cancel for library scan: {}", libraryId);
        }
    }

    private boolean isCancelled(Integer libraryId) {
        return Boolean.TRUE.equals(cancelledScans.get(libraryId));
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
        cancelledScans.put(libraryId, false);
        if (activeScans.putIfAbsent(libraryId, progress) != null) {
            cancelledScans.remove(libraryId);
            log.info("Scan already running for library: {}", libraryId);
            sseService.broadcast("scan-end", "Scan already running for library ID: " + libraryId);
            return;
        }
        broadcastProgress(progress);

        try {
            int repairedPaths = reconcileStoredFilePaths(library);
            if (repairedPaths > 0) {
                log.info("Rewrote {} stale file_path entries for library {}", repairedPaths, libraryId);
            }
            int restored = restoreDeletedFilesStillOnDisk(library);
            if (restored > 0) {
                log.info("Restored {} files incorrectly marked deleted for library {}", restored, libraryId);
            }

            AtomicInteger totalFiles = new AtomicInteger(0);
            AtomicInteger matchedFiles = new AtomicInteger(0);
            AtomicInteger newFiles = new AtomicInteger(0);
            AtomicInteger updatedFiles = new AtomicInteger(0);
            AtomicInteger restoredFiles = new AtomicInteger(restored);
            AtomicInteger failedFiles = new AtomicInteger(0);

            for (LibraryPath path : library.getPaths()) {
                if (isCancelled(libraryId)) {
                    break;
                }
                progress.setCurrentPath(path.getPath());
                scanDirectory(library, path.getPath(), totalFiles, matchedFiles, newFiles,
                        updatedFiles, restoredFiles, failedFiles, progress);
            }

            if (isCancelled(libraryId)) {
                applyProgressCounts(progress, totalFiles, matchedFiles, newFiles);
                progress.setStatus("CANCELLED");
                progress.setUpdatedAt(System.currentTimeMillis());
                broadcastProgress(progress);
                log.info("Scan cancelled for library: {}", libraryId);
                sseService.broadcast("scan-end", "Scan cancelled for library ID: " + libraryId);
                SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                        .timestamp(System.currentTimeMillis())
                        .level("INFO")
                        .source("TASK")
                        .type("SCAN_CANCELLED")
                        .libraryId(libraryId)
                        .message("Scan cancelled for library ID: " + libraryId)
                        .build());
                return;
            }

            int missingFiles = reconcileMissingFiles(library);

            updateLastScannedAt(libraryId);

            applyProgressCounts(progress, totalFiles, matchedFiles, newFiles);
            progress.setStatus("DONE");
            progress.setUpdatedAt(System.currentTimeMillis());
            broadcastProgress(progress);

            String summary = String.format(
                    "Scan complete for '%s': scanned %d files, %d matched type [%s], %d new, %d updated, %d restored, %d failed, %d missing files hidden",
                    library.getName(), totalFiles.get(), matchedFiles.get(), library.getType(),
                    newFiles.get(), updatedFiles.get(), restoredFiles.get(), failedFiles.get(), missingFiles);
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

            sseService.broadcast("library.updated", Map.of("libraryId", libraryId, "action", "scan_completed"));

            if (matchedFiles.get() == 0 && totalFiles.get() > 0) {
                String hint = String.format(
                        "Hint: Library '%s' type is [%s], but none of the %d files had a matching extension. " +
                        "Expected extensions: %s. Check if the library type is correct.",
                        library.getName(), library.getType(), totalFiles.get(),
                        fileScanProcessor.getExpectedExtensions(library.getType()));
                log.warn(hint);
                broadcastScanMessage(hint);
                SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                        .timestamp(System.currentTimeMillis())
                        .level("WARN")
                        .source("TASK")
                        .type("SCAN_WARNING")
                        .libraryId(libraryId)
                        .message(hint)
                        .build());
            }
        } catch (Exception e) {
            progress.setStatus("ERROR");
            progress.setUpdatedAt(System.currentTimeMillis());
            broadcastProgress(progress);
            log.error("Scan failed for library {}", libraryId, e);
            sseService.broadcast("scan-end", "Scan failed for library ID: " + libraryId);
            SystemLogBroadcaster.getInstance().broadcast(SystemLogEventDto.builder()
                    .timestamp(System.currentTimeMillis())
                    .level("ERROR")
                    .source("TASK")
                    .type("SCAN_ERROR")
                    .libraryId(libraryId)
                    .message("Scan failed: " + e.getMessage())
                    .build());
        } finally {
            activeScans.remove(libraryId);
            cancelledScans.remove(libraryId);
        }
    }

    @Async
    public void scanSpecificDirectoryAsync(String dirPathStr) {
        String resolvedDirPath = storagePathMapper.mapPathIfNeeded(dirPathStr);
        Path targetDir = Paths.get(resolvedDirPath);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) return;

        MediaLibrary lib = findLibraryForPath(dirPathStr);
        if (lib == null) return;

        log.info("Starting targeted directory scan for path: {}", targetDir);

        ScanProgressDTO progress = ScanProgressDTO.builder()
                .libraryId(lib.getId())
                .libraryName(lib.getName())
                .status("SCANNING")
                .currentPath(targetDir.toString())
                .totalFiles(0)
                .scannedFiles(0)
                .matchedFiles(0)
                .newItems(0)
                .startedAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        if (activeScans.putIfAbsent(lib.getId(), progress) != null) {
            log.info("Skipping targeted scan because library scan is already running: {}", lib.getId());
            return;
        }
        broadcastProgress(progress);

        broadcastScanMessage("Processing files in recent changed directory");
        AtomicInteger total = new AtomicInteger(0);
        AtomicInteger matched = new AtomicInteger(0);
        AtomicInteger added = new AtomicInteger(0);
        AtomicInteger updated = new AtomicInteger(0);
        AtomicInteger restored = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        try {
            scanDirectory(lib, targetDir.toString(), total, matched, added,
                    updated, restored, failed, progress);
            int missing = reconcileMissingFiles(lib, targetDir);
            updateLastScannedAt(lib.getId());
            applyProgressCounts(progress, total, matched, added);
            progress.setStatus("DONE");
            progress.setUpdatedAt(System.currentTimeMillis());
            broadcastProgress(progress);
            broadcastScanMessage(String.format(
                    "Targeted scan done: %d files, %d matched, %d new, %d updated, %d restored, %d failed, %d missing",
                    total.get(), matched.get(), added.get(), updated.get(), restored.get(), failed.get(), missing));
        } finally {
            activeScans.remove(lib.getId());
        }
    }

    @Transactional(readOnly = true)
    public MediaLibrary loadLibrary(Integer libraryId) {
        return libraryRepository.findWithDetailsById(libraryId).orElse(null);
    }

    @Transactional(readOnly = true)
    public MediaLibrary findLibraryForPath(String dirPathStr) {
        List<Path> changedPaths = storagePathCandidates(dirPathStr).stream()
                .map(Paths::get)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        return libraryRepository.findAll().stream()
                .filter(lib -> lib.getPaths().stream()
                        .map(lp -> storagePathMapper.mapPathIfNeeded(lp.getPath()))
                        .map(Paths::get)
                        .map(path -> path.toAbsolutePath().normalize())
                        .anyMatch(root -> changedPaths.stream().anyMatch(changedPath -> changedPath.startsWith(root))))
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
                               AtomicInteger updatedFiles, AtomicInteger restoredFiles, AtomicInteger failedFiles,
                               ScanProgressDTO progress) {
        String resolvedDirectoryPath = storagePathMapper.mapPathIfNeeded(directoryPath);
        Path startPath = Paths.get(resolvedDirectoryPath);
        if (!Files.exists(startPath) || !Files.isDirectory(startPath)) {
            log.warn("Directory does not exist or is not a directory: {} (resolved: {})", directoryPath, resolvedDirectoryPath);
            broadcastScanMessage("Path not found: " + directoryPath);
            return;
        }

        log.info("Scanning directory: {} (resolved: {})", directoryPath, resolvedDirectoryPath);
        broadcastScanMessage("Scanning: " + directoryPath);

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCancelled(library.getId())) {
                        return FileVisitResult.TERMINATE;
                    }
                    totalFiles.incrementAndGet();

                    FileScanProcessor.ScanOutcome outcome;
                    try {
                        outcome = fileScanProcessor.scanFile(library, file, attrs);
                    } catch (Exception e) {
                        log.error("Failed to scan file: {}", file, e);
                        outcome = FileScanProcessor.ScanOutcome.FAILED;
                    }
                    if (!outcome.matchedMediaFile()) {
                        throttledProgressUpdate(progress, totalFiles, matchedFiles, newFiles, file);
                        return FileVisitResult.CONTINUE;
                    }

                    matchedFiles.incrementAndGet();
                    switch (outcome) {
                        case CREATED -> newFiles.incrementAndGet();
                        case UPDATED -> updatedFiles.incrementAndGet();
                        case RESTORED -> restoredFiles.incrementAndGet();
                        case FAILED -> failedFiles.incrementAndGet();
                        default -> {
                            // UNCHANGED is still a matched media file, but no counters need to move.
                        }
                    }

                    throttledProgressUpdate(progress, totalFiles, matchedFiles, newFiles, file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    if (isCancelled(library.getId())) {
                        return FileVisitResult.TERMINATE;
                    }
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

    /**
     * When library roots change (e.g. /home/test_media -> /home/media), DB rows may keep the old prefix.
     * Rewrite to the path that actually exists on disk (mapped path or same relative path under current roots).
     */
    @Transactional
    public int reconcileStoredFilePaths(MediaLibrary library) {
        if (library == null || library.getId() == null || library.getPaths().isEmpty()) {
            return 0;
        }
        List<Path> libraryRoots = library.getPaths().stream()
                .map(LibraryPath::getPath)
                .map(storagePathMapper::mapPathIfNeeded)
                .map(Paths::get)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();

        int updated = 0;
        for (MediaFile file : mediaFileRepository.findActiveByLibraryId(library.getId())) {
            String stored = file.getFilePath();
            if (stored == null || stored.isBlank()) {
                continue;
            }
            String storedNorm = stored.replace('\\', '/');
            String resolved = resolveExistingStoredPath(storedNorm, libraryRoots);
            if (resolved != null && !resolved.equals(storedNorm)) {
                file.setFilePath(resolved);
                mediaFileRepository.save(file);
                updated++;
            }
        }
        return updated;
    }

    private String resolveExistingStoredPath(String storedNorm, List<Path> libraryRoots) {
        String mapped = storagePathMapper.mapPathIfNeeded(storedNorm).replace('\\', '/');
        if (Files.exists(Paths.get(mapped))) {
            return mapped;
        }
        if (Files.exists(Paths.get(storedNorm))) {
            return storedNorm;
        }
        int firstSlash = storedNorm.indexOf('/', 1);
        if (firstSlash < 0 || firstSlash == storedNorm.length() - 1) {
            return null;
        }
        String relative = storedNorm.substring(firstSlash + 1);
        for (Path root : libraryRoots) {
            Path candidate = root.resolve(relative).normalize();
            if (Files.exists(candidate)) {
                return candidate.toString().replace('\\', '/');
            }
        }
        return null;
    }

    @Transactional
    public int restoreDeletedFilesStillOnDisk(MediaLibrary library) {
        if (library == null || library.getId() == null) {
            return 0;
        }
        List<Path> libraryRoots = library.getPaths().stream()
                .map(LibraryPath::getPath)
                .map(storagePathMapper::mapPathIfNeeded)
                .map(Paths::get)
                .map(path -> path.toAbsolutePath().normalize())
                .toList();

        int restored = 0;
        for (MediaFile file : mediaFileRepository.findAllByLibraryId(library.getId())) {
            if (!Boolean.TRUE.equals(file.getDeleted())) {
                continue;
            }
            String resolved = resolveExistingStoredPath(
                    file.getFilePath() != null ? file.getFilePath().replace('\\', '/') : "",
                    libraryRoots);
            if (resolved == null) {
                continue;
            }
            mediaFileLifecycleService.restoreFile(file, resolved);
            restored++;
        }
        return restored;
    }

    @Transactional
    public int reconcileMissingFiles(MediaLibrary library) {
        return reconcileMissingFiles(library, null);
    }

    @Transactional
    public int reconcileMissingFiles(MediaLibrary library, Path scopeDirectory) {
        if (library == null || library.getId() == null || library.getPaths().isEmpty()) {
            return 0;
        }

        int missing = 0;
        Path normalizedScope = scopeDirectory != null ? scopeDirectory.toAbsolutePath().normalize() : null;
        for (MediaFile file : mediaFileRepository.findActiveByLibraryId(library.getId())) {
            if (normalizedScope != null && !isStoredFileUnderScope(file.getFilePath(), normalizedScope)) {
                continue;
            }
            if (mediaFileLifecycleService.fileExistsOnDisk(file.getFilePath())) {
                continue;
            }
            if (mediaFileLifecycleService.softDeleteFile(file)) {
                missing++;
            }
        }
        if (missing > 0) {
            log.info("Marked {} missing files as deleted for library {}", missing, library.getId());
        }
        return missing;
    }

    private void throttledProgressUpdate(ScanProgressDTO progress,
                                         AtomicInteger totalFiles, AtomicInteger matchedFiles,
                                         AtomicInteger newFiles, Path currentFile) {
        if (progress == null) return;
        int total = totalFiles.get();
        if (total % THROTTLE_FILE_COUNT != 0) return;

        applyProgressCounts(progress, totalFiles, matchedFiles, newFiles);
        progress.setCurrentPath(currentFile.getParent() != null ? currentFile.getParent().toString() : "");
        progress.setUpdatedAt(System.currentTimeMillis());
        broadcastProgress(progress);
    }

    private void applyProgressCounts(ScanProgressDTO progress,
                                     AtomicInteger totalFiles,
                                     AtomicInteger matchedFiles,
                                     AtomicInteger newFiles) {
        if (progress == null) {
            return;
        }
        int total = totalFiles.get();
        progress.setTotalFiles(total);
        progress.setScannedFiles(total);
        progress.setMatchedFiles(matchedFiles.get());
        progress.setNewItems(newFiles.get());
    }

    private boolean isStoredFileUnderScope(String storedPath, Path normalizedScope) {
        if (storedPath == null || storedPath.isBlank()) {
            return false;
        }
        for (String candidate : storagePathCandidates(storedPath)) {
            try {
                Path path = Paths.get(candidate).toAbsolutePath().normalize();
                if (path.startsWith(normalizedScope)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Invalid persisted path; let full-library reconciliation handle it.
            }
        }
        return false;
    }

    private List<String> storagePathCandidates(String storedPath) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String mapped = storagePathMapper.mapPathIfNeeded(storedPath);
        if (mapped != null && !mapped.isBlank()) {
            candidates.add(mapped.replace('\\', '/'));
        }
        candidates.add(storedPath.replace('\\', '/'));
        return candidates.stream().toList();
    }

    private void broadcastProgress(ScanProgressDTO progress) {
        sseService.broadcastBoth("scan-status", "scan.progress", progress);
    }

    private void broadcastScanMessage(String message) {
        sseService.broadcastBoth("scan-progress", "scan.progress", message);
    }
}
