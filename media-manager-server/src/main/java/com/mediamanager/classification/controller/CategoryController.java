package com.mediamanager.classification.controller;

import com.mediamanager.classification.dto.*;
import com.mediamanager.classification.service.CategoryService;
import com.mediamanager.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Category", description = "Category Management")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('category:view') or hasAuthority('category:manage')")
    @Operation(summary = "Get category tree")
    public ApiResponse<List<CategoryResponse>> getCategoryTree() {
        return ApiResponse.success(categoryService.getCategoryTree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Create a new category")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        return ApiResponse.success(categoryService.createCategory(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Update category")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Integer id,
                                                        @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.success(categoryService.updateCategory(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Delete category")
    public ApiResponse<Void> deleteCategory(@PathVariable Integer id) {
        categoryService.deleteCategory(id);
        return ApiResponse.success();
    }
}
