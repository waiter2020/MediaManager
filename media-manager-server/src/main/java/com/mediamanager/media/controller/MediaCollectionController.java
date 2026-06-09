package com.mediamanager.media.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaCollectionCreateRequest;
import com.mediamanager.media.dto.MediaCollectionItemsRequest;
import com.mediamanager.media.dto.MediaCollectionResponse;
import com.mediamanager.media.dto.MediaCollectionUpdateRequest;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.service.MediaCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@Tag(name = "Media Collections", description = "Collections and playlists")
public class MediaCollectionController {

    private final MediaCollectionService collectionService;

    @GetMapping
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "List collections visible to current user")
    public ApiResponse<List<MediaCollectionResponse>> listCollections() {
        return ApiResponse.success(collectionService.listCollections());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Create a collection or playlist")
    public ApiResponse<MediaCollectionResponse> createCollection(
            @Valid @RequestBody MediaCollectionCreateRequest request) {
        return ApiResponse.success(collectionService.createCollection(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get collection details and items")
    public ApiResponse<MediaCollectionResponse> getCollection(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "true") boolean includeItems) {
        return ApiResponse.success(collectionService.getCollection(id, includeItems));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get collection items with pagination")
    public ApiResponse<PageResult<MediaItemResponse>> getCollectionItems(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return ApiResponse.success(collectionService.getCollectionItems(id, page, size, sortField, sortOrder));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Update collection metadata")
    public ApiResponse<MediaCollectionResponse> updateCollection(
            @PathVariable Integer id,
            @Valid @RequestBody MediaCollectionUpdateRequest request) {
        return ApiResponse.success(collectionService.updateCollection(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Delete a collection")
    public ApiResponse<Void> deleteCollection(@PathVariable Integer id) {
        collectionService.deleteCollection(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Add media items to a collection")
    public ApiResponse<MediaCollectionResponse> addItems(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "true") boolean includeItems,
            @Valid @RequestBody MediaCollectionItemsRequest request) {
        return ApiResponse.success(collectionService.addItems(id, request, includeItems));
    }

    @DeleteMapping("/{id}/items/{mediaItemId}")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Remove a media item from a collection")
    public ApiResponse<MediaCollectionResponse> removeItem(
            @PathVariable Integer id,
            @PathVariable Integer mediaItemId,
            @RequestParam(defaultValue = "true") boolean includeItems) {
        return ApiResponse.success(collectionService.removeItem(id, mediaItemId, includeItems));
    }
}
