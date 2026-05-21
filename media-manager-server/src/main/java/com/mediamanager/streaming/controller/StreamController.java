package com.mediamanager.streaming.controller;

import com.mediamanager.streaming.service.HlsStreamingService;
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
    private final HlsStreamingService hlsStreamingService;

    @GetMapping("/{fileId}/playback")
    @PreAuthorize("hasAuthority('media:play')")
    public java.util.Map<String, String> playbackInfo(@PathVariable Integer fileId) {
        HlsStreamingService.PlaybackInfo info = hlsStreamingService.resolvePlaybackInfo(fileId);
        return java.util.Map.of("mode", info.mode(), "url", info.url());
    }

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

    @GetMapping("/{fileId}/hls/master.m3u8")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> hlsMaster(@PathVariable Integer fileId) {
        Resource resource = hlsStreamingService.getMasterPlaylist(fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(resource);
    }

    @GetMapping("/{fileId}/hls/{segment}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> hlsSegment(
            @PathVariable Integer fileId,
            @PathVariable String segment) {
        Resource resource = hlsStreamingService.getSegment(fileId, segment);
        String contentType = segment.endsWith(".m3u8")
                ? "application/vnd.apple.mpegurl"
                : "video/mp2t";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }
}
