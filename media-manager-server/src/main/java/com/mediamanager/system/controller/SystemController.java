package com.mediamanager.system.controller;

import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.system.repository.SysConfigRepository;
import com.mediamanager.system.repository.SysUserRepository;
import com.mediamanager.system.dto.DirectoryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "System", description = "System status APIs")
public class SystemController {

    private final SysUserRepository userRepository;
    private final SysConfigRepository configRepository;
    private final MediaLibraryRepository libraryRepository;
    private final MediaItemRepository mediaItemRepository;
    private final TagRepository tagRepository;

    @GetMapping("/status")
    @Operation(summary = "Get system status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("setupCompleted", userRepository.count() > 0);
        status.put("version", "1.0.0");
        return ApiResponse.success(status);
    }

    @GetMapping("/directories")
    @Operation(summary = "List server directories")
    public ApiResponse<List<DirectoryDTO>> listDirectories(@RequestParam(required = false) String path) {
        List<DirectoryDTO> directories = new ArrayList<>();
        if (path == null || path.trim().isEmpty()) {
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    directories.add(DirectoryDTO.builder()
                            .name(root.getAbsolutePath())
                            .path(root.getAbsolutePath())
                            .hasChildren(hasSubDirectories(root))
                            .build());
                }
            }
        } else {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles(File::isDirectory);
                if (files != null) {
                    Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                    for (File file : files) {
                        if (!file.isHidden()) {
                            directories.add(DirectoryDTO.builder()
                                    .name(file.getName())
                                    .path(file.getAbsolutePath())
                                    .hasChildren(hasSubDirectories(file))
                                    .build());
                        }
                    }
                }
            }
        }
        return ApiResponse.success(directories);
    }

    private boolean hasSubDirectories(File dir) {
        try {
            File[] files = dir.listFiles(File::isDirectory);
            return files != null && files.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Get all system configs")
    public ApiResponse<List<Map<String, String>>> getConfigs() {
        List<Map<String, String>> configs = configRepository.findAll().stream()
                .map(c -> Map.of(
                        "key", c.getConfigKey(),
                        "value", c.getConfigValue() != null ? c.getConfigValue() : "",
                        "description", c.getDescription() != null ? c.getDescription() : ""
                ))
                .collect(Collectors.toList());
        return ApiResponse.success(configs);
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('system:manage')")
    @Operation(summary = "Update system config")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, String> configMap) {
        configMap.forEach((key, value) -> {
            configRepository.findByConfigKey(key).ifPresent(config -> {
                config.setConfigValue(value);
                configRepository.save(config);
            });
        });
        return ApiResponse.success();
    }

    @GetMapping("/info")
    @Operation(summary = "Get system info")
    public ApiResponse<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", "1.0.0");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("totalUsers", userRepository.count());
        info.put("totalLibraries", libraryRepository.count());
        info.put("totalMediaItems", mediaItemRepository.count());
        info.put("setupCompleted", userRepository.count() > 0);
        // Per-type media counts for dashboard
        info.put("videoCount", mediaItemRepository.countByType("MOVIE") + mediaItemRepository.countByType("TV_SHOW"));
        info.put("imageCount", mediaItemRepository.countByType("IMAGE"));
        info.put("audioCount", mediaItemRepository.countByType("AUDIO"));
        info.put("tagCount", tagRepository.count());
        return ApiResponse.success(info);
    }
}

