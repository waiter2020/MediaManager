package com.mediamanager.classification.controller;

import com.mediamanager.classification.dto.*;
import com.mediamanager.classification.service.TagService;
import com.mediamanager.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tag", description = "Tag Management")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @PreAuthorize("hasAuthority('tag:view') or hasAuthority('tag:manage')")
    @Operation(summary = "Get all tags")
    public ApiResponse<List<TagResponse>> getAllTags() {
        return ApiResponse.success(tagService.getAllTags());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tag:manage')")
    @Operation(summary = "Create a new tag")
    public ApiResponse<TagResponse> createTag(@Valid @RequestBody TagCreateRequest request) {
        return ApiResponse.success(tagService.createTag(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tag:manage')")
    @Operation(summary = "Update tag")
    public ApiResponse<TagResponse> updateTag(@PathVariable Integer id,
                                              @RequestBody TagUpdateRequest request) {
        return ApiResponse.success(tagService.updateTag(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('tag:manage')")
    @Operation(summary = "Delete tag")
    public ApiResponse<Void> deleteTag(@PathVariable Integer id) {
        tagService.deleteTag(id);
        return ApiResponse.success();
    }

    @PostMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('tag:assign')")
    @Operation(summary = "Add tag to media item")
    public ApiResponse<Void> addTagToItem(@PathVariable Integer itemId,
                                          @RequestParam Integer tagId) {
        tagService.addTagToItem(itemId, tagId);
        return ApiResponse.success();
    }

    @DeleteMapping("/items/{itemId}/{tagId}")
    @PreAuthorize("hasAuthority('tag:assign')")
    @Operation(summary = "Remove tag from media item")
    public ApiResponse<Void> removeTagFromItem(@PathVariable Integer itemId,
                                               @PathVariable Integer tagId) {
        tagService.removeTagFromItem(itemId, tagId);
        return ApiResponse.success();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('tag:assign')")
    @Operation(summary = "Batch add tags to media items")
    public ApiResponse<Void> batchAddTags(@RequestBody BatchTagRequest request) {
        tagService.batchAddTags(request);
        return ApiResponse.success();
    }
}
