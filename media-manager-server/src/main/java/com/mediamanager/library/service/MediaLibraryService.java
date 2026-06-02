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
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.common.security.SecurityCurrentUser;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.service.LibraryPluginConfigService;
import com.mediamanager.sync.service.DirectoryWatcherService;
import com.mediamanager.sync.service.SseService;
import com.mediamanager.system.entity.LibraryAccess;
import com.mediamanager.system.repository.LibraryAccessRepository;
import com.mediamanager.system.service.LibraryAccessService;
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
    private final MediaFileRepository mediaFileRepository;
    private final LibraryAccessService libraryAccessService;
    private final LibraryPluginConfigService libraryPluginConfigService;
    private final SecurityCurrentUser securityCurrentUser;
    private final SseService sseService;
    private final LibraryAccessRepository libraryAccessRepository;
    private final DirectoryWatcherService directoryWatcherService;

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
        grantCreatorAccess(library.getId());
        if (request.getExtractors() == null || request.getExtractors().isEmpty()) {
            libraryPluginConfigService.ensureDefaultExtractorConfigs(library.getId());
        } else {
            libraryPluginConfigService.syncFromExtractorConfigs(library.getId());
        }
        directoryWatcherService.refreshLibraryPaths(library);
        sseService.broadcast("library.updated", Map.of("libraryId", library.getId(), "action", "created"));
        return enrichWithPlugins(mapper.toResponse(
                libraryRepository.findWithDetailsById(library.getId()).orElse(library)));
    }

    @Transactional(readOnly = true)
    public List<MediaLibraryResponse> getAllLibraries() {
        var allowed = libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser());
        return libraryRepository.findAll().stream()
                .filter(lib -> allowed.contains(lib.getId()))
                .map(lib -> {
                    MediaLibraryResponse resp = mapper.toResponse(lib);
                    resp.setTotalItems(mediaItemRepository.countByLibrary_IdAndHiddenFalse(lib.getId()));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public MediaLibraryResponse getLibraryById(Integer id) {
        libraryAccessService.assertCanViewLibrary(id);
        libraryPluginConfigService.ensureDefaultExtractorConfigs(id);
        MediaLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));
        MediaLibraryResponse resp = enrichWithPlugins(mapper.toResponse(library));
        resp.setTotalItems(mediaItemRepository.countByLibrary_IdAndHiddenFalse(id));
        return resp;
    }

    private MediaLibraryResponse enrichWithPlugins(MediaLibraryResponse resp) {
        if (resp == null || resp.getId() == null) {
            return resp;
        }
        List<Map<String, Object>> rows = libraryPluginConfigService.listForLibrary(resp.getId());
        List<MediaLibraryResponse.PluginRes> plugins = rows.stream()
                .map(row -> MediaLibraryResponse.PluginRes.builder()
                        .pluginId(String.valueOf(row.get("pluginId")))
                        .kind(String.valueOf(row.get("kind")))
                        .enabled(Boolean.parseBoolean(String.valueOf(row.get("enabled"))))
                        .priority(Integer.parseInt(String.valueOf(row.get("priority"))))
                        .config(String.valueOf(row.get("config")))
                        .build())
                .collect(Collectors.toList());
        resp.setPlugins(plugins);
        resp.setExtractors(plugins.stream()
                .filter(p -> PluginKind.EXTRACTOR.name().equals(p.getKind()))
                .map(p -> MediaLibraryResponse.ExtractorRes.builder()
                        .type(p.getPluginId().toUpperCase())
                        .priority(p.getPriority())
                        .enabled(p.getEnabled())
                        .config(p.getConfig())
                        .build())
                .collect(Collectors.toList()));
        return resp;
    }

    @Transactional
    public MediaLibraryResponse updateLibrary(Integer id, MediaLibraryUpdateRequest request) {
        libraryAccessService.assertCanEditLibrary(id);
        MediaLibrary library = libraryRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));

        List<String> oldPaths = library.getPaths().stream()
                .map(LibraryPath::getPath)
                .toList();

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
            libraryRepository.save(library);
            libraryPluginConfigService.syncFromExtractorConfigs(id);
        } else {
            libraryRepository.save(library);
        }

        if (request.getPaths() != null) {
            List<String> newPaths = library.getPaths().stream()
                    .map(LibraryPath::getPath)
                    .toList();
            migrateMediaFilePathsOnLibraryPathChange(id, oldPaths, newPaths);
        }

        directoryWatcherService.refreshLibraryPaths(library);
        sseService.broadcast("library.updated", Map.of("libraryId", id, "action", "updated"));
        return enrichWithPlugins(mapper.toResponse(
                libraryRepository.findWithDetailsById(id).orElse(library)));
    }

    private void migrateMediaFilePathsOnLibraryPathChange(Integer libraryId, List<String> oldPaths, List<String> newPaths) {
        if (oldPaths == null || newPaths == null || oldPaths.isEmpty() || newPaths.isEmpty()) {
            return;
        }
        // Heuristic: if a root path was changed, try to rewrite stored filePath by keeping the suffix.
        // Example: /home/test_media/foo/bar.mp4 -> /home/media/foo/bar.mp4 (if the new file exists).
        List<String> removed = oldPaths.stream().filter(p -> newPaths.stream().noneMatch(n -> samePathPrefix(n, p))).toList();
        if (removed.isEmpty()) {
            return;
        }

        int updated = 0;
        for (MediaFile file : mediaFileRepository.findActiveByLibraryId(libraryId)) {
            String stored = normalizeSlashes(file.getFilePath());
            if (stored == null || stored.isBlank()) {
                continue;
            }
            for (String fromRootRaw : removed) {
                String fromRoot = normalizeSlashes(fromRootRaw);
                if (!isPathPrefixIgnoreCase(stored, fromRoot)) {
                    continue;
                }
                String suffix = stored.substring(stripTrailingSlash(fromRoot).length());
                if (!suffix.startsWith("/")) {
                    suffix = "/" + suffix;
                }
                for (String toRootRaw : newPaths) {
                    String toRoot = normalizeSlashes(toRootRaw);
                    String candidate = stripTrailingSlash(toRoot) + suffix;
                    try {
                        if (java.nio.file.Files.exists(java.nio.file.Paths.get(candidate))) {
                            file.setFilePath(candidate);
                            mediaFileRepository.save(file);
                            updated++;
                            break;
                        }
                    } catch (Exception ignored) {
                        // Ignore invalid paths; reconciliation/scan will handle deletions if unreachable.
                    }
                }
                break;
            }
        }
        if (updated > 0) {
            sseService.broadcast("library.updated", Map.of("libraryId", libraryId, "action", "paths_migrated", "updatedFiles", updated));
        }
    }

    private String normalizeSlashes(String p) {
        return p == null ? null : p.replace('\\', '/');
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return value;
        String result = value;
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isPathPrefixIgnoreCase(String path, String prefix) {
        if (path == null || prefix == null) return false;
        String p = stripTrailingSlash(normalizeSlashes(path));
        String from = stripTrailingSlash(normalizeSlashes(prefix));
        if (from.isBlank()) return false;
        if (p.length() < from.length()) return false;
        if (!p.regionMatches(true, 0, from, 0, from.length())) return false;
        return p.length() == from.length() || p.charAt(from.length()) == '/';
    }

    private boolean samePathPrefix(String a, String b) {
        return isPathPrefixIgnoreCase(normalizeSlashes(a), normalizeSlashes(b))
                && isPathPrefixIgnoreCase(normalizeSlashes(b), normalizeSlashes(a));
    }

    @Transactional
    public void deleteLibrary(Integer id) {
        libraryAccessService.assertCanDeleteLibrary(id);
        MediaLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));
        libraryRepository.delete(library);
        directoryWatcherService.unregisterLibrary(id);
        sseService.broadcast("library.updated", Map.of("libraryId", id, "action", "deleted"));
    }

    @Transactional
    public void triggerScan(Integer id) {
        libraryAccessService.assertCanScanLibrary(id);
        if (!libraryRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.LIBRARY_NOT_FOUND);
        }
        scanService.scanLibraryAsync(id);
    }

    public Map<String, Object> getLibraryStats(Integer id) {
        libraryAccessService.assertCanViewLibrary(id);
        MediaLibrary library = libraryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));

        Map<String, Object> stats = new HashMap<>();
        long itemCount = mediaItemRepository.countByLibrary_IdAndHiddenFalse(id);
        stats.put("totalItems", itemCount);
        stats.put("libraryName", library.getName());
        stats.put("libraryType", library.getType());
        stats.put("lastScannedAt", library.getLastScannedAt());
        return stats;
    }

    private void grantCreatorAccess(Integer libraryId) {
        securityCurrentUser.getCurrentUser().ifPresent(user -> {
            if (libraryAccessService.bypassesLibraryRestrictions(user)) {
                return;
            }
            LibraryAccess access = LibraryAccess.builder()
                    .user(user)
                    .libraryId(libraryId)
                    .canView(true)
                    .canEdit(true)
                    .canDeleteFile(true)
                    .build();
            libraryAccessRepository.save(access);
        });
    }
}
