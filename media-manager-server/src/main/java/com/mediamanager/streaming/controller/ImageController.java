package com.mediamanager.streaming.controller;

import com.mediamanager.streaming.service.StreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * API contract alias for {@code GET /api/v1/images/{fileId}}.
 */
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final StreamService streamService;

    @GetMapping("/{fileId}")
    @PreAuthorize("hasAuthority('media:view')")
    public ResponseEntity<Resource> getImage(
            @PathVariable Integer fileId,
            @RequestParam(required = false) Integer w) throws IOException {
        return streamService.getImageResource(fileId, w);
    }
}
