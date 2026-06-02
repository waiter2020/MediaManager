package com.mediamanager.media.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.dto.MediaFileDto;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecycleBinService {

    private final MediaFileRepository fileRepository;
    private final LibraryAccessService libraryAccessService;
    private final MediaFileLifecycleService mediaFileLifecycleService;

    @Transactional(readOnly = true)
    public List<MediaFileDto> listDeleted() {
        return fileRepository.findDeletedWithItemAndLibrary().stream()
                .filter(file -> {
                    try {
                        libraryAccessService.assertCanViewRecycleFile(file);
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                })
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void restore(Integer fileId) {
        MediaFile file = fileRepository.findByIdWithItemAndLibrary(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        libraryAccessService.assertCanViewRecycleFile(file);
        mediaFileLifecycleService.restoreFile(file, file.getFilePath());
    }

    @Transactional
    public void purgeRecord(Integer fileId) {
        purgeRecord(fileId, false);
    }

    @Transactional
    public void purgeRecord(Integer fileId, boolean deleteSourceFile) {
        MediaFile file = fileRepository.findByIdWithItemAndLibrary(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        libraryAccessService.assertCanPurgeRecycleFile(file);
        mediaFileLifecycleService.purgeRecord(file, deleteSourceFile, false);
    }

    /** System job: permanently remove expired soft-deleted files (all libraries). */
    @Transactional
    public int purgeExpired(Instant before) {
        return mediaFileLifecycleService.purgeExpired(before);
    }

    private MediaFileDto toDto(MediaFile file) {
        MediaItem item = file.getMediaItem();
        return MediaFileDto.builder()
                .id(file.getId())
                .mediaItemId(item != null ? item.getId() : null)
                .mediaTitle(item != null ? item.getTitle() : null)
                .libraryName(item != null && item.getLibrary() != null ? item.getLibrary().getName() : null)
                .deletedAt(file.getDeletedAt())
                .filePath(file.getFilePath())
                .fileName(file.getFileName())
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .container(file.getContainer())
                .build();
    }
}
