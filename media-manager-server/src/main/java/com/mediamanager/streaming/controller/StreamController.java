package com.mediamanager.streaming.controller;

import com.mediamanager.streaming.service.StreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    @GetMapping("/{fileId}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<ResourceRegion> streamMedia(
            @PathVariable Integer fileId,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = streamService.getMediaResource(fileId);
        ResourceRegion region = streamService.getResourceRegion(resource, headers);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(region);
    }
    
    @GetMapping("/raw/{fileId}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> getRawFile(@PathVariable Integer fileId) throws IOException {
        Resource resource = streamService.getMediaResource(fileId);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/images/{fileId}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> getImage(
            @PathVariable Integer fileId,
            @RequestParam(required = false) Integer w) throws IOException {
        return streamService.getImageResource(fileId, w);
    }
}
