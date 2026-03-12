package com.mediamanager.sync.service;

import com.mediamanager.library.entity.LibraryPath;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.library.service.LibraryScanService;
import com.mediamanager.sync.util.EventDebouncer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.file.StandardWatchEventKinds.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectoryWatcherService {

    private final MediaLibraryRepository libraryRepository;
    private final LibraryScanService scanService;
    private final EventDebouncer debouncer;
    
    private WatchService watchService;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private volatile boolean running = false;

    @PostConstruct
    public void init() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.running = true;
            
            // Register all active library paths
            libraryRepository.findAll().forEach(this::registerLibraryPaths);
            
            // Start listening loop
            executorService.submit(this::processEvents);
            log.info("DirectoryWatcherService initialized.");
        } catch (IOException e) {
            log.error("Failed to initialize WatchService", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        this.running = false;
        try {
            if (watchService != null) watchService.close();
            executorService.shutdown();
        } catch (IOException e) {
            log.error("Error closing WatchService", e);
        }
    }

    public void registerLibraryPaths(MediaLibrary library) {
        for (LibraryPath path : library.getPaths()) {
            registerDirectoryAndSubDirectories(Paths.get(path.getPath()), library.getId());
        }
    }

    private void registerDirectoryAndSubDirectories(Path start, Integer libraryId) {
        if (!Files.exists(start) || !Files.isDirectory(start)) {
            log.warn("Cannot watch missing or non-directory path: {}", start);
            return;
        }

        try {
            Files.walk(start)
                 .filter(Files::isDirectory)
                 .forEach(p -> registerDirectory(p, libraryId));
            log.info("Registered watcher for base path: {}", start);
        } catch (IOException e) {
            log.error("Error walking directory for watch registration: {}", start, e);
        }
    }

    private void registerDirectory(Path dir, Integer libraryId) {
        try {
            // Mapping libraryId to path could be useful later, for now we scan by library on event
            WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            keys.put(key, dir);
        } catch (IOException e) {
            log.error("Failed to register directory {}", dir, e);
        }
    }

    private void processEvents() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                log.warn("WatchKey not recognized!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                
                if (kind == OVERFLOW) continue;

                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                log.debug("File System Event: {} on {}", kind.name(), child);

                // If a new directory is created, register it
                if (kind == ENTRY_CREATE && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    registerDirectoryAndSubDirectories(child, null);
                }
                
                // Debounce the scan operation for the parent directory's library
                // To keep it simple, we trigger an async scan of the parent folder or whole library
                // Assuming standard naming, we'll just scan the folder that changed.
                String debounceKey = "scan_dir_" + dir.toString();
                debouncer.debounce(debounceKey, () -> {
                    log.info("Debounced file event triggered. Scanning changed directory: {}", dir);
                    // Locate library. We could map this better during registration, 
                    // for now just trigger a fast custom scan
                    scanService.scanSpecificDirectoryAsync(dir.toString());
                }, 3000); // 3-second debounce
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    log.info("All watch keys became invalid.");
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }
}
