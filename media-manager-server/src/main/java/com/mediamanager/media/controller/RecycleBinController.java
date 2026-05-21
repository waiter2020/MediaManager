package com.mediamanager.media.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.media.dto.MediaFileDto;
import com.mediamanager.media.service.RecycleBinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recycle-bin")
@RequiredArgsConstructor
@Tag(name = "Recycle Bin", description = "Soft-deleted media files")
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    @GetMapping
    @PreAuthorize("hasAuthority('media:view')")
    @Operation(summary = "List soft-deleted files")
    public ApiResponse<List<MediaFileDto>> list() {
        return ApiResponse.success(recycleBinService.listDeleted());
    }

    @PostMapping("/{fileId}/restore")
    @PreAuthorize("hasAuthority('media:delete')")
    @Operation(summary = "Restore soft-deleted file")
    public ApiResponse<Void> restore(@PathVariable Integer fileId) {
        recycleBinService.restore(fileId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAuthority('media:delete')")
    @Operation(summary = "Permanently remove file record")
    public ApiResponse<Void> purge(@PathVariable Integer fileId) {
        recycleBinService.purgeRecord(fileId);
        return ApiResponse.success();
    }
}
