package com.mediamanager.media.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.dto.MediaFileDto;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.search.service.FtsIndexService;
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
    private final MediaItemRepository itemRepository;
    private final FtsIndexService ftsIndexService;
    private final LibraryAccessService libraryAccessService;

    @Transactional(readOnly = true)
    public List<MediaFileDto> listDeleted() {
        return fileRepository.findDeletedWithItemAndLibrary().stream()
                .filter(file -> {
                    try {
                        libraryAccessService.assertCanViewFile(file);
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
        libraryAccessService.assertCanViewFile(file);
        if (!Boolean.TRUE.equals(file.getDeleted())) {
            return;
        }
        file.setDeleted(false);
        file.setDeletedAt(null);
        fileRepository.save(file);
        MediaItem item = file.getMediaItem();
        if (Boolean.TRUE.equals(item.getHidden())) {
            item.setHidden(false);
            itemRepository.save(item);
            ftsIndexService.indexItem(item);
        }
    }

    @Transactional
    public void purgeRecord(Integer fileId) {
        MediaFile file = fileRepository.findByIdWithItemAndLibrary(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        libraryAccessService.assertCanViewFile(file);
        MediaItem item = file.getMediaItem();
        Integer itemId = item.getId();
        fileRepository.delete(file);
        if (fileRepository.findByMediaItemId(itemId).isEmpty()) {
            ftsIndexService.removeItem(itemId);
            itemRepository.deleteById(itemId);
        }
    }

    @Transactional
    public int purgeExpired(Instant before) {
        List<MediaFile> expired = fileRepository.findDeletedBefore(before);
        int count = 0;
        for (MediaFile file : expired) {
            Integer itemId = file.getMediaItem() != null ? file.getMediaItem().getId() : null;
            fileRepository.delete(file);
            count++;
            if (itemId != null && fileRepository.findByMediaItemId(itemId).isEmpty()) {
                ftsIndexService.removeItem(itemId);
                itemRepository.deleteById(itemId);
            }
        }
        return count;
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
