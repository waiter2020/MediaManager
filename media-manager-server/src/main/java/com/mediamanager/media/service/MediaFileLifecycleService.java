package com.mediamanager.media.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaFileLifecycleService {

    private final MediaFileRepository fileRepository;
    private final MediaItemRepository itemRepository;
    private final MediaPostProcessService mediaPostProcessService;
    private final StoragePathMapper storagePathMapper;

    public boolean fileExistsOnDisk(String storedPath) {
        return resolveExistingPath(storedPath).isPresent();
    }

    public Optional<String> resolveExistingPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return Optional.empty();
        }

        String mapped = normalize(storagePathMapper.mapPathIfNeeded(storedPath));
        if (Files.exists(Path.of(mapped))) {
            return Optional.of(mapped);
        }

        String raw = normalize(storedPath);
        if (!raw.equals(mapped) && Files.exists(Path.of(raw))) {
            return Optional.of(raw);
        }

        return Optional.empty();
    }

    @Transactional
    public void restoreFile(MediaFile file, String resolvedPath) {
        if (file == null) {
            return;
        }
        if (resolvedPath != null && !resolvedPath.isBlank()) {
            file.setFilePath(normalize(resolvedPath));
        }
        file.setDeleted(false);
        file.setDeletedAt(null);
        fileRepository.save(file);

        MediaItem item = file.getMediaItem();
        if (item != null) {
            item.setHidden(false);
            itemRepository.save(item);
            mediaPostProcessService.syncSearchIndexes(item);
        }
    }

    @Transactional
    public boolean softDeleteFile(MediaFile file) {
        if (file == null || Boolean.TRUE.equals(file.getDeleted())) {
            return false;
        }
        file.setDeleted(true);
        file.setDeletedAt(Instant.now());
        fileRepository.save(file);
        syncItemVisibility(file.getMediaItem());
        return true;
    }

    @Transactional
    public int softDeleteActiveFiles(MediaItem item) {
        if (item == null || item.getId() == null) {
            return 0;
        }
        int deleted = 0;
        for (MediaFile file : fileRepository.findByMediaItemIdAndDeletedFalse(item.getId())) {
            file.setDeleted(true);
            file.setDeletedAt(Instant.now());
            fileRepository.save(file);
            deleted++;
        }
        syncItemVisibility(item);
        return deleted;
    }

    @Transactional
    public int deleteSourceFiles(MediaItem item) {
        if (item == null || item.getId() == null) {
            return 0;
        }
        int deleted = 0;
        for (MediaFile file : fileRepository.findByMediaItemIdAndDeletedFalse(item.getId())) {
            deletePhysicalFile(file, true);
            file.setDeleted(true);
            file.setDeletedAt(Instant.now());
            fileRepository.save(file);
            deleted++;
        }
        syncItemVisibility(item);
        return deleted;
    }

    @Transactional
    public void purgeRecord(MediaFile file, boolean deleteSourceFile, boolean failOnPhysicalDelete) {
        if (file == null) {
            return;
        }
        if (deleteSourceFile) {
            deletePhysicalFile(file, failOnPhysicalDelete);
        }

        MediaItem item = file.getMediaItem();
        Integer itemId = item != null ? item.getId() : null;
        fileRepository.delete(file);

        if (itemId == null) {
            return;
        }
        if (fileRepository.findByMediaItemId(itemId).isEmpty()) {
            mediaPostProcessService.removeSearchIndexes(itemId);
            itemRepository.deleteById(itemId);
        } else if (item != null) {
            syncItemVisibility(item);
        }
    }

    @Transactional
    public int purgeExpired(Instant before) {
        int count = 0;
        for (MediaFile file : fileRepository.findDeletedBefore(before)) {
            purgeRecord(file, false, false);
            count++;
        }
        return count;
    }

    @Transactional
    public void syncItemVisibility(MediaItem item) {
        if (item == null || item.getId() == null) {
            return;
        }
        boolean hasActiveFiles = !fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).isEmpty();
        item.setHidden(!hasActiveFiles);
        itemRepository.save(item);
        mediaPostProcessService.syncSearchIndexes(item);
    }

    private void deletePhysicalFile(MediaFile file, boolean failOnError) {
        if (file.getFilePath() == null || file.getFilePath().isBlank()) {
            return;
        }
        String resolvedPath = normalize(storagePathMapper.mapPathIfNeeded(file.getFilePath()));
        try {
            Files.deleteIfExists(Path.of(resolvedPath));
            log.info("Deleted physical media file: {} (stored: {})", resolvedPath, file.getFilePath());
        } catch (IOException e) {
            if (failOnError) {
                throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Failed to delete: " + file.getFilePath());
            }
            log.warn("Failed to delete physical media file {}: {}", file.getFilePath(), e.getMessage());
        }
    }

    private String normalize(String path) {
        return path == null ? null : path.replace('\\', '/');
    }
}
