package com.mediamanager.media.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.dto.MediaItemDetailResponse;
import com.mediamanager.metadata.dto.IdentifyRequest;
import com.mediamanager.media.service.MediaItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
@Tag(name = "Media Item", description = "Media Item Browsing APIs")
public class MediaItemController {

    private final MediaItemService itemService;

    @GetMapping
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get media items with pagination and filters")
    public ApiResponse<PageResult<MediaItemResponse>> getItems(
            @RequestParam(required = false) Integer libraryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Set<Integer> categoryIds,
            @RequestParam(required = false) Set<Integer> tagIds,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return ApiResponse.success(itemService.getItems(
                libraryId, type, keyword, categoryIds, tagIds, page, size, sortField, sortOrder));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get single media item by ID")
    public ApiResponse<MediaItemResponse> getItem(@PathVariable Integer id) {
        return ApiResponse.success(itemService.getItem(id));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get detailed media item by ID")
    public ApiResponse<MediaItemDetailResponse> getItemDetail(@PathVariable Integer id) {
        return ApiResponse.success(itemService.getItemDetail(id));
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get available filter options")
    public ApiResponse<Map<String, Object>> getFilters() {
        return ApiResponse.success(itemService.getFilterOptions());
    }

    @PutMapping("/{id}/metadata")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Update media item metadata")
    public ApiResponse<MediaItemResponse> updateMetadata(@PathVariable Integer id,
                                                         @RequestBody Map<String, Object> metadata) {
        return ApiResponse.success(itemService.updateMetadata(id, metadata));
    }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasAuthority('media:refresh')")
    @Operation(summary = "Refresh media item metadata")
    public ApiResponse<Void> refreshMetadata(@PathVariable Integer id) {
        itemService.refreshMetadata(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/identify")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Manually identify item with external provider")
    public ApiResponse<MediaItemResponse> identify(
            @PathVariable Integer id,
            @RequestBody IdentifyRequest request) {
        return ApiResponse.success(itemService.identifyItem(id, request));
    }

    @GetMapping("/{id}/tmdb/search")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Search TMDb candidates for manual match")
    public ApiResponse<List<Map<String, Object>>> searchTmdb(
            @PathVariable Integer id,
            @RequestParam String q) {
        return ApiResponse.success(itemService.searchTmdbCandidates(id, q));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('media:delete')")
    @Operation(summary = "Delete media item")
    public ApiResponse<Void> deleteItem(@PathVariable Integer id) {
        itemService.deleteItem(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}/file")
    @PreAuthorize("hasAuthority('media:delete_file')")
    @Operation(summary = "Delete source file")
    public ApiResponse<Void> deleteFile(@PathVariable Integer id) {
        itemService.deleteSourceFile(id);
        return ApiResponse.success();
    }

    @GetMapping("/{id}/poster")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get poster image")
    public ResponseEntity<Resource> getPoster(@PathVariable Integer id) throws IOException {
        return itemService.getImage(id, "poster");
    }

    @GetMapping("/{id}/backdrop")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get backdrop image")
    public ResponseEntity<Resource> getBackdrop(@PathVariable Integer id) throws IOException {
        return itemService.getImage(id, "backdrop");
    }
}
