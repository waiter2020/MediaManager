package com.mediamanager.search.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.search.dto.NlSearchResult;
import com.mediamanager.search.dto.SearchReindexStatus;
import com.mediamanager.search.dto.SemanticSearchResult;
import com.mediamanager.search.dto.UnifiedSearchResult;
import com.mediamanager.search.service.SearchReindexJobService;
import com.mediamanager.search.service.SearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Keyword, semantic and NL search")
public class SearchController {

    private final SearchService searchService;
    private final SearchReindexJobService searchReindexJobService;

    @GetMapping
    @PreAuthorize("hasAuthority('media:view')")
    public ApiResponse<PageResult<MediaItemResponse>> keyword(
            @RequestParam String q,
            @RequestParam(required = false) Integer libraryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(searchService.keywordSearch(q, libraryId, page, size));
    }

    @PostMapping("/semantic")
    @PreAuthorize("hasAuthority('media:view')")
    public ApiResponse<?> semantic(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.get("query"));
        Integer libraryId = body.get("libraryId") != null ? Integer.parseInt(String.valueOf(body.get("libraryId"))) : null;
        
        if (body.containsKey("page") && body.containsKey("size")) {
            int page = Integer.parseInt(String.valueOf(body.get("page")));
            int size = Integer.parseInt(String.valueOf(body.get("size")));
            return ApiResponse.success(searchService.semanticSearch(query, libraryId, page, size));
        }
        
        int limit = body.get("limit") != null ? Integer.parseInt(String.valueOf(body.get("limit"))) : 20;
        SemanticSearchResult result = searchService.semanticSearch(query, libraryId, limit);
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

    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<SearchReindexStatus> reindex() {
        if (!searchReindexJobService.startRebuildAll()) {
            return ApiResponse.<SearchReindexStatus>builder()
                    .code(200)
                    .message("索引重建已在进行中")
                    .data(searchReindexJobService.getStatus())
                    .timestamp(java.time.Instant.now())
                    .build();
        }
        return ApiResponse.<SearchReindexStatus>builder()
                .code(200)
                .message("索引重建已启动，请通过 /search/reindex/status 查询进度")
                .data(searchReindexJobService.getStatus())
                .timestamp(java.time.Instant.now())
                .build();
    }

    @GetMapping("/reindex/status")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<SearchReindexStatus> reindexStatus() {
        return ApiResponse.success(searchReindexJobService.getStatus());
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('media:view')")
    public ApiResponse<NlSearchResult> nlQuery(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.get("query"));
        Integer libraryId = body.get("libraryId") != null ? Integer.parseInt(String.valueOf(body.get("libraryId"))) : null;
        int page = body.get("page") != null ? Integer.parseInt(String.valueOf(body.get("page"))) : 1;
        int size = body.get("size") != null ? Integer.parseInt(String.valueOf(body.get("size"))) : 20;
        return ApiResponse.success(searchService.naturalLanguageSearch(query, libraryId, page, size));
    }

    @PostMapping("/unified")
    @PreAuthorize("hasAuthority('media:view')")
    public ApiResponse<UnifiedSearchResult> unified(@RequestBody Map<String, Object> body) {
        String query = body.get("query") != null ? String.valueOf(body.get("query")) : "";
        Integer libraryId = body.get("libraryId") != null ? Integer.parseInt(String.valueOf(body.get("libraryId"))) : null;
        String type = body.get("type") != null ? String.valueOf(body.get("type")) : null;
        Integer minYear = body.get("minYear") != null ? Integer.parseInt(String.valueOf(body.get("minYear"))) : null;
        Integer maxYear = body.get("maxYear") != null ? Integer.parseInt(String.valueOf(body.get("maxYear"))) : null;
        Double minRating = body.get("minRating") != null ? Double.parseDouble(String.valueOf(body.get("minRating"))) : null;
        int page = body.get("page") != null ? Integer.parseInt(String.valueOf(body.get("page"))) : 1;
        int size = body.get("size") != null ? Integer.parseInt(String.valueOf(body.get("size"))) : 20;

        return ApiResponse.success(searchService.unifiedSearch(
                query,
                libraryId,
                type,
                parseIntegerSet(body.get("categoryIds")),
                parseIntegerSet(body.get("tagIds")),
                minYear,
                maxYear,
                minRating,
                page,
                size));
    }

    private Set<Integer> parseIntegerSet(Object value) {
        if (value == null) {
            return null;
        }
        Set<Integer> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addInteger(result, item);
            }
            return result;
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        for (String part : str.split(",")) {
            addInteger(result, part);
        }
        return result.isEmpty() ? null : result;
    }

    private void addInteger(Set<Integer> result, Object value) {
        if (value == null) {
            return;
        }
        String str = String.valueOf(value).trim();
        if (!str.isEmpty()) {
            result.add(Integer.parseInt(str));
        }
    }
}
