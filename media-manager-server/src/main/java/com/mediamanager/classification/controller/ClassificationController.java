package com.mediamanager.classification.controller;

import com.mediamanager.classification.dto.CategoryResponse;
import com.mediamanager.classification.dto.TagResponse;
import com.mediamanager.classification.service.ClassificationService;
import com.mediamanager.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/classification")
@RequiredArgsConstructor
public class ClassificationController {

    private final ClassificationService classificationService;

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('tag:view') or hasAuthority('tag:manage')")
    @Operation(summary = "Get all tags")
    public ApiResponse<List<TagResponse>> getAllTags() {
        return ApiResponse.success(classificationService.getAllTags());
    }

    @GetMapping("/categories/tree")
    @PreAuthorize("hasAuthority('category:view') or hasAuthority('category:manage')")
    @Operation(summary = "Get category tree")
    public ApiResponse<List<CategoryResponse>> getCategoryTree() {
        return ApiResponse.success(classificationService.getCategoryTree());
    }
}
