package com.mediamanager.search.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.search.service.SearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Keyword, semantic and NL search")
public class SearchController {

    private final SearchService searchService;

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
    public ApiResponse<List<MediaItemResponse>> semantic(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.get("query"));
        Integer libraryId = body.get("libraryId") != null ? Integer.parseInt(String.valueOf(body.get("libraryId"))) : null;
        int limit = body.get("limit") != null ? Integer.parseInt(String.valueOf(body.get("limit"))) : 20;
        return ApiResponse.success(searchService.semanticSearch(query, libraryId, limit));
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('system:manage')")
    public ApiResponse<Map<String, Object>> reindex() {
        int count = searchService.rebuildFtsIndex();
        return ApiResponse.success(Map.of("indexed", count));
    }

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('media:view')")
    public ApiResponse<PageResult<MediaItemResponse>> nlQuery(@RequestBody Map<String, Object> body) {
        String query = String.valueOf(body.get("query"));
        Integer libraryId = body.get("libraryId") != null ? Integer.parseInt(String.valueOf(body.get("libraryId"))) : null;
        int page = body.get("page") != null ? Integer.parseInt(String.valueOf(body.get("page"))) : 1;
        int size = body.get("size") != null ? Integer.parseInt(String.valueOf(body.get("size"))) : 20;
        return ApiResponse.success(searchService.naturalLanguageSearch(query, libraryId, page, size));
    }
}
