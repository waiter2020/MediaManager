package com.mediamanager.classification.controller;

import com.mediamanager.classification.dto.ClassificationRuleRequest;
import com.mediamanager.classification.dto.ClassificationRuleResponse;
import com.mediamanager.classification.service.ClassificationRuleService;
import com.mediamanager.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/classification-rules")
@RequiredArgsConstructor
@Tag(name = "Classification Rule", description = "Classification Rule Management")
public class ClassificationRuleController {

    private final ClassificationRuleService ruleService;

    @GetMapping
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Get all classification rules")
    public ApiResponse<List<ClassificationRuleResponse>> getAllRules() {
        return ApiResponse.success(ruleService.getAllRules());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Create a classification rule")
    public ApiResponse<ClassificationRuleResponse> createRule(@Valid @RequestBody ClassificationRuleRequest request) {
        return ApiResponse.success(ruleService.createRule(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Update a classification rule")
    public ApiResponse<ClassificationRuleResponse> updateRule(@PathVariable Integer id,
                                                              @RequestBody ClassificationRuleRequest request) {
        return ApiResponse.success(ruleService.updateRule(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "Delete a classification rule")
    public ApiResponse<Void> deleteRule(@PathVariable Integer id) {
        ruleService.deleteRule(id);
        return ApiResponse.success();
    }
}
