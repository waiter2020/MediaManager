package com.mediamanager.ai.controller;

import com.mediamanager.ai.service.AiSuggestionService;
import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.security.SecurityCurrentUser;
import com.mediamanager.system.entity.SysUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/suggestions")
@RequiredArgsConstructor
@Tag(name = "AI Suggestions", description = "Review AI metadata suggestions")
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;
    private final SecurityCurrentUser securityCurrentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    public ApiResponse<List<Map<String, Object>>> listPending() {
        return ApiResponse.success(aiSuggestionService.listPending());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    public ApiResponse<Void> approve(@PathVariable Integer id) {
        Integer reviewerId = securityCurrentUser.getCurrentUser().map(SysUser::getId).orElse(null);
        aiSuggestionService.approve(id, reviewerId);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    public ApiResponse<Void> reject(@PathVariable Integer id) {
        Integer reviewerId = securityCurrentUser.getCurrentUser().map(SysUser::getId).orElse(null);
        aiSuggestionService.reject(id, reviewerId);
        return ApiResponse.success();
    }

    @PostMapping("/batch-approve")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    public ApiResponse<Map<String, Integer>> batchApprove(@RequestBody Map<String, List<Integer>> body) {
        Integer reviewerId = securityCurrentUser.getCurrentUser().map(SysUser::getId).orElse(null);
        List<Integer> ids = body != null ? body.get("ids") : List.of();
        int count = aiSuggestionService.batchApprove(ids, reviewerId);
        return ApiResponse.success(Map.of("approved", count));
    }

    @PostMapping("/batch-reject")
    @PreAuthorize("hasAuthority('media:edit_metadata')")
    public ApiResponse<Map<String, Integer>> batchReject(@RequestBody Map<String, List<Integer>> body) {
        Integer reviewerId = securityCurrentUser.getCurrentUser().map(SysUser::getId).orElse(null);
        List<Integer> ids = body != null ? body.get("ids") : List.of();
        int count = aiSuggestionService.batchReject(ids, reviewerId);
        return ApiResponse.success(Map.of("rejected", count));
    }
}
