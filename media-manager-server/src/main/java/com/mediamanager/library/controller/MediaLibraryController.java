package com.mediamanager.library.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.library.dto.MediaLibraryCreateRequest;
import com.mediamanager.library.dto.MediaLibraryResponse;
import com.mediamanager.library.dto.MediaLibraryUpdateRequest;
import com.mediamanager.classification.service.LibraryClassifyJobService;
import com.mediamanager.library.service.MediaLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/libraries")
@RequiredArgsConstructor
@Tag(name = "Library", description = "Media Library Management")
public class MediaLibraryController {

    private final MediaLibraryService libraryService;
    private final LibraryClassifyJobService libraryClassifyJobService;

    @PostMapping
    @PreAuthorize("hasAuthority('library:create')")
    @Operation(summary = "Create a new media library")
    public ApiResponse<MediaLibraryResponse> createLibrary(@Valid @RequestBody MediaLibraryCreateRequest request) {
        return ApiResponse.success(libraryService.createLibrary(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('library:view')")
    @Operation(summary = "Get all media libraries")
    public ApiResponse<List<MediaLibraryResponse>> getAllLibraries() {
        return ApiResponse.success(libraryService.getAllLibraries());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('library:view')")
    @Operation(summary = "Get media library by ID")
    public ApiResponse<MediaLibraryResponse> getLibraryById(@PathVariable Integer id) {
        return ApiResponse.success(libraryService.getLibraryById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('library:edit')")
    @Operation(summary = "Update media library")
    public ApiResponse<MediaLibraryResponse> updateLibrary(@PathVariable Integer id,
                                                           @RequestBody MediaLibraryUpdateRequest request) {
        return ApiResponse.success(libraryService.updateLibrary(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('library:delete')")
    @Operation(summary = "Delete media library")
    public ApiResponse<Void> deleteLibrary(@PathVariable Integer id) {
        libraryService.deleteLibrary(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/scan")
    @PreAuthorize("hasAuthority('library:scan')")
    @Operation(summary = "Trigger library scan")
    public ApiResponse<Void> triggerScan(@PathVariable Integer id) {
        libraryService.triggerScan(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAuthority('library:view')")
    @Operation(summary = "Get library statistics")
    public ApiResponse<Map<String, Object>> getLibraryStats(@PathVariable Integer id) {
        return ApiResponse.success(libraryService.getLibraryStats(id));
    }

    @PostMapping("/{id}/classify")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Re-run classification for all visible items in library (async)")
    public ApiResponse<Map<String, Object>> classifyLibrary(@PathVariable Integer id) {
        boolean started = libraryClassifyJobService.startLibraryClassify(id);
        if (!started) {
            Map<String, Object> status = libraryClassifyJobService.getStatus();
            return ApiResponse.success(Map.of(
                    "accepted", false,
                    "message", status.get("message"),
                    "status", status));
        }
        return ApiResponse.success(Map.of(
                "accepted", true,
                "message", "库级分类任务已启动",
                "status", libraryClassifyJobService.getStatus()));
    }

    @GetMapping("/classify/status")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Library-wide classification job status")
    public ApiResponse<Map<String, Object>> classifyLibraryStatus() {
        return ApiResponse.success(libraryClassifyJobService.getStatus());
    }

    @PostMapping("/classify/cancel")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Cancel the running library-wide AI classification job")
    public ApiResponse<Boolean> cancelLibraryClassify() {
        return ApiResponse.success(libraryClassifyJobService.cancel());
    }
}
