package com.mediamanager.plugin.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.plugin.PluginRegistry;
import com.mediamanager.plugin.service.LibraryPluginConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Plugins", description = "Plugin registry and library configuration")
public class PluginController {

    private final PluginRegistry pluginRegistry;
    private final LibraryPluginConfigService libraryPluginConfigService;

    @GetMapping("/plugins")
    @PreAuthorize("hasAuthority('library:view')")
    public ApiResponse<List<Map<String, Object>>> listPlugins() {
        return ApiResponse.success(pluginRegistry.listDescriptors());
    }

    @GetMapping("/libraries/{libraryId}/plugins")
    @PreAuthorize("hasAuthority('library:view')")
    public ApiResponse<List<Map<String, Object>>> listLibraryPlugins(@PathVariable Integer libraryId) {
        return ApiResponse.success(libraryPluginConfigService.listForLibrary(libraryId));
    }

    @PutMapping("/libraries/{libraryId}/plugins")
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<Void> updateLibraryPlugins(
            @PathVariable Integer libraryId,
            @RequestBody List<Map<String, Object>> configs) {
        libraryPluginConfigService.replaceConfigs(libraryId, configs);
        return ApiResponse.success();
    }

    @PostMapping("/libraries/{libraryId}/plugins/apply-default")
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<List<Map<String, Object>>> applyDefaultPlugins(@PathVariable Integer libraryId) {
        return ApiResponse.success(libraryPluginConfigService.applyDefaultTemplate(libraryId));
    }
}
