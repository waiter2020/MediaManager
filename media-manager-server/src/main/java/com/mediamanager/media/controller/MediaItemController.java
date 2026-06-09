package com.mediamanager.media.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.ClassifyBatchRequest;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.dto.MediaItemDetailResponse;
import com.mediamanager.media.dto.MediaSubtitleDto;
import com.mediamanager.media.dto.SubtitleDownloadRequest;
import com.mediamanager.media.dto.SubtitleSearchResultDto;
import com.mediamanager.metadata.dto.IdentifyRequest;
import com.mediamanager.metadata.dto.SeasonDto;
import com.mediamanager.media.service.MediaItemService;
import com.mediamanager.media.service.MediaChapterService;
import com.mediamanager.media.service.MediaSubtitleService;
import com.mediamanager.search.dto.SemanticSearchResult;
import com.mediamanager.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final MediaChapterService chapterService;
    private final MediaSubtitleService subtitleService;
    private final SearchService searchService;

    @GetMapping
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get media items with pagination and filters")
    public ApiResponse<PageResult<MediaItemResponse>> getItems(
            @RequestParam(required = false) Integer libraryId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Set<Integer> categoryIds,
            @RequestParam(required = false) Set<Integer> tagIds,
            @RequestParam(required = false) Integer minYear,
            @RequestParam(required = false) Integer maxYear,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return ApiResponse.success(itemService.getItems(
                libraryId, type, keyword, categoryIds, tagIds, minYear, maxYear, minRating,
                page, size, sortField, sortOrder));
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

    @GetMapping("/{id}/seasons")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get TV show seasons and episodes")
    public ApiResponse<List<SeasonDto>> getItemSeasons(@PathVariable Integer id) {
        return ApiResponse.success(itemService.getItemSeasons(id));
    }

    @GetMapping("/{id}/subtitles")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get subtitles attached to a media item")
    public ApiResponse<List<MediaSubtitleDto>> getItemSubtitles(@PathVariable Integer id) {
        return ApiResponse.success(subtitleService.getSubtitlesForItem(id));
    }

    @GetMapping("/chapters/{chapterId}/thumbnail")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Get a generated video chapter thumbnail")
    public ResponseEntity<Resource> getChapterThumbnail(@PathVariable Integer chapterId) {
        return chapterService.getChapterThumbnail(chapterId);
    }

    @GetMapping("/{id}/subtitles/search")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Search online subtitle candidates")
    public ApiResponse<List<SubtitleSearchResultDto>> searchOnlineSubtitles(
            @PathVariable Integer id,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) Integer fileId) {
        return ApiResponse.success(subtitleService.searchOnlineSubtitles(id, q, language, fileId));
    }

    @PostMapping("/{id}/subtitles/download")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Download and attach an online subtitle")
    public ApiResponse<MediaSubtitleDto> downloadSubtitle(
            @PathVariable Integer id,
            @Valid @RequestBody SubtitleDownloadRequest request) {
        return ApiResponse.success(subtitleService.downloadAndAttachSubtitle(
                id,
                request.getProvider(),
                request.getExternalId(),
                request.getFileId(),
                request.getLanguage()));
    }

    @PostMapping("/{id}/seasons/sync")
    @PreAuthorize("hasAuthority('media:refresh')")
    @Operation(summary = "Sync TV seasons and episodes from TMDb")
    public ApiResponse<Map<String, Object>> syncTvSeasons(@PathVariable Integer id) {
        return ApiResponse.success(itemService.syncTvSeasons(id));
    }

    @GetMapping("/{id}/similar")
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "Find semantically similar media items")
    public ApiResponse<SemanticSearchResult> getSimilarItems(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "12") int limit) {
        SemanticSearchResult result = searchService.similarToItem(id, Math.min(Math.max(limit, 1), 50));
        if (result.getHint() != null && !result.getHint().isBlank()) {
            return ApiResponse.<SemanticSearchResult>builder()
                    .code(200)
                    .message(result.getHint())
                    .data(result)
                    .timestamp(java.time.Instant.now())
                    .build();
        }
        return ApiResponse.success(result);
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

    @PostMapping("/{id}/classify")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Re-run classification for media item")
    public ApiResponse<Void> classifyItem(@PathVariable Integer id) {
        itemService.classifyItem(id);
        return ApiResponse.success();
    }

    @PostMapping("/classify-batch")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Re-run classification for multiple items (max 100)")
    public ApiResponse<Map<String, Object>> classifyBatch(@Valid @RequestBody ClassifyBatchRequest request) {
        return ApiResponse.success(itemService.classifyBatch(request.getItemIds()));
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

    @GetMapping("/{id}/javbus/search")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Search JavBus candidates for manual match")
    public ApiResponse<List<Map<String, Object>>> searchJavBus(
            @PathVariable Integer id,
            @RequestParam String q) {
        return ApiResponse.success(itemService.searchJavBusCandidates(id, q));
    }

    @GetMapping("/{id}/stashdb/search")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    @Operation(summary = "Search StashDB candidates for manual match")
    public ApiResponse<List<Map<String, Object>>> searchStashDb(
            @PathVariable Integer id,
            @RequestParam String q) {
        return ApiResponse.success(itemService.searchStashDbCandidates(id, q));
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
