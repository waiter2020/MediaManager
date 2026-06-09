package com.mediamanager.ai.controller;

import com.mediamanager.ai.dto.AiOrganizationRequest;
import com.mediamanager.ai.dto.AiOrganizationJobStatus;
import com.mediamanager.ai.dto.AiOrganizationResponse;
import com.mediamanager.ai.service.AiOrganizationJobService;
import com.mediamanager.ai.service.AiOrganizationService;
import com.mediamanager.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/organization")
@RequiredArgsConstructor
@Tag(name = "AI Organization", description = "AI-assisted tag and collection organization")
public class AiOrganizationController {

    private final AiOrganizationService organizationService;
    private final AiOrganizationJobService organizationJobService;

    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('tag:manage') or hasAuthority('media:edit_metadata')")
    @Operation(summary = "Preview tag cleanup and smart collection candidates")
    public ApiResponse<AiOrganizationResponse> preview(
            @RequestParam(required = false) Integer libraryId,
            @RequestParam(required = false) Boolean mergeDuplicateTags,
            @RequestParam(required = false) Boolean deleteUnusedTags,
            @RequestParam(required = false) Boolean deleteLowUsageTags,
            @RequestParam(required = false) Boolean protectManualTags,
            @RequestParam(required = false) Boolean recolorTags,
            @RequestParam(required = false) Boolean recolorManualTags,
            @RequestParam(required = false) Boolean createSmartCollections,
            @RequestParam(required = false) Integer lowUsageThreshold,
            @RequestParam(required = false) Integer maxCollections,
            @RequestParam(required = false) Integer minCollectionTagUsage,
            @RequestParam(required = false) Integer minTagCollectionUsage,
            @RequestParam(required = false) Integer collectionItemLimit) {
        AiOrganizationRequest request = new AiOrganizationRequest();
        request.setLibraryId(libraryId);
        request.setMergeDuplicateTags(mergeDuplicateTags);
        request.setDeleteUnusedTags(deleteUnusedTags);
        request.setDeleteLowUsageTags(deleteLowUsageTags);
        request.setProtectManualTags(protectManualTags);
        request.setRecolorTags(recolorTags);
        request.setRecolorManualTags(recolorManualTags);
        request.setCreateSmartCollections(createSmartCollections);
        request.setLowUsageThreshold(lowUsageThreshold);
        request.setMaxCollections(maxCollections);
        request.setMinCollectionTagUsage(minCollectionTagUsage);
        request.setMinTagCollectionUsage(minTagCollectionUsage);
        request.setCollectionItemLimit(collectionItemLimit);
        return ApiResponse.success(organizationService.preview(request));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('tag:manage') and hasAuthority('media:edit_metadata')")
    @Operation(summary = "Start tag cleanup and smart collection creation in the background")
    public ApiResponse<Map<String, Object>> apply(@RequestBody AiOrganizationRequest request) {
        boolean accepted = organizationJobService.start(request);
        AiOrganizationJobStatus status = organizationJobService.getStatus();
        return ApiResponse.success(Map.of(
                "accepted", accepted,
                "message", accepted ? "整理任务已进入后台队列" : status.getMessage(),
                "status", status));
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('tag:manage') or hasAuthority('media:edit_metadata')")
    @Operation(summary = "Get AI organization job status")
    public ApiResponse<AiOrganizationJobStatus> status() {
        return ApiResponse.success(organizationJobService.getStatus());
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('tag:manage') and hasAuthority('media:edit_metadata')")
    @Operation(summary = "Cancel the running AI organization job")
    public ApiResponse<Boolean> cancel() {
        return ApiResponse.success(organizationJobService.cancel());
    }
}
