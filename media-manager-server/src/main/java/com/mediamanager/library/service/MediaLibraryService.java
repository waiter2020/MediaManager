package com.mediamanager.library.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.library.dto.MediaLibraryCreateRequest;
import com.mediamanager.library.dto.MediaLibraryResponse;
import com.mediamanager.library.dto.MediaLibraryUpdateRequest;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.library.entity.LibraryPath;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.mapper.MediaLibraryMapper;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaLibraryService {

    private final MediaLibraryRepository libraryRepository;
    private final MediaLibraryMapper mapper;
    private final LibraryScanService scanService;
    private final MediaItemRepository mediaItemRepository;

    @Transactional
    public MediaLibraryResponse createLibrary(MediaLibraryCreateRequest request) {
        MediaLibrary library = MediaLibrary.builder()
                .name(request.getName())
                .type(request.getType())
                .language(request.getLanguage() != null ? request.getLanguage() : "zh")
                .autoScan(request.getAutoScan() != null ? request.getAutoScan() : true)
                .scanIntervalMinutes(request.getScanIntervalMinutes() != null ? request.getScanIntervalMinutes() : 30)
                .build();

        if (request.getPaths() != null) {
            for (MediaLibraryCreateRequest.PathReq p : request.getPaths()) {
                library.addPath(LibraryPath.builder()
                        .path(p.getPath())
                        .priority(p.getPriority() != null ? p.getPriority() : 0)
                        .build());
            }
        }

        if (request.getExtractors() != null) {
            for (MediaLibraryCreateRequest.ExtractorReq e : request.getExtractors()) {
                library.addExtractorConfig(LibraryExtractorConfig.builder()
                        .extractorType(e.getType())
                        .priority(e.getPriority() != null ? e.getPriority() : 0)
                        .enabled(e.getEnabled() != null ? e.getEnabled() : true)
                        .config(e.getConfig())
                        .build());
            }
        }

        libraryRepository.save(library);
        return mapper.toResponse(library);
    }

    @Transactional(readOnly = true)
    public List<MediaLibraryResponse> getAllLibraries() {
        return libraryRepository.findAll().stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MediaLibraryResponse getLibraryById(Integer id) {
        MediaLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));
        return mapper.toResponse(library);
    }

    @Transactional
    public MediaLibraryResponse updateLibrary(Integer id, MediaLibraryUpdateRequest request) {
        MediaLibrary library = libraryRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));

        if (request.getName() != null) library.setName(request.getName());
        if (request.getType() != null) library.setType(request.getType());
        if (request.getLanguage() != null) library.setLanguage(request.getLanguage());
        if (request.getAutoScan() != null) library.setAutoScan(request.getAutoScan());
        if (request.getScanIntervalMinutes() != null) library.setScanIntervalMinutes(request.getScanIntervalMinutes());

        if (request.getPaths() != null) {
            library.getPaths().clear();
            for (MediaLibraryUpdateRequest.PathReq p : request.getPaths()) {
                library.addPath(LibraryPath.builder()
                        .path(p.getPath())
                        .priority(p.getPriority() != null ? p.getPriority() : 0)
                        .build());
            }
        }

        if (request.getExtractors() != null) {
            library.getExtractorConfigs().clear();
            for (MediaLibraryUpdateRequest.ExtractorReq e : request.getExtractors()) {
                library.addExtractorConfig(LibraryExtractorConfig.builder()
                        .extractorType(e.getType())
                        .priority(e.getPriority() != null ? e.getPriority() : 0)
                        .enabled(e.getEnabled() != null ? e.getEnabled() : true)
                        .config(e.getConfig())
                        .build());
            }
        }

        libraryRepository.save(library);
        return mapper.toResponse(library);
    }

    @Transactional
    public void deleteLibrary(Integer id) {
        MediaLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));
        libraryRepository.delete(library);
    }

    @Transactional
    public void triggerScan(Integer id) {
        if (!libraryRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.LIBRARY_NOT_FOUND);
        }
        scanService.scanLibraryAsync(id);
    }

    public Map<String, Object> getLibraryStats(Integer id) {
        MediaLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));

        Map<String, Object> stats = new HashMap<>();
        long itemCount = mediaItemRepository.countByLibrary_Id(id);
        stats.put("totalItems", itemCount);
        stats.put("libraryName", library.getName());
        stats.put("libraryType", library.getType());
        stats.put("lastScannedAt", library.getLastScannedAt());
        return stats;
    }
}
