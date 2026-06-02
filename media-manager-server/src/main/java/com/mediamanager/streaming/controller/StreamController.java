package com.mediamanager.streaming.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.security.JwtTokenProvider;
import com.mediamanager.streaming.service.HlsStreamingService;
import com.mediamanager.streaming.service.StreamService;
import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stream")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;
    private final HlsStreamingService hlsStreamingService;
    private final JwtTokenProvider tokenProvider;

    private static final int STREAM_TOKEN_TTL_SECONDS = 300; // 5 minutes

    @PostMapping("/token")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<ApiResponse<Void>> issueStreamToken(
            @AuthenticationPrincipal SysUser user,
            HttpServletResponse response) {
        var permissions = UserService.collectPermissions(user);
        String token = tokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), permissions);

        Cookie cookie = new Cookie("mm_stream_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // set true behind HTTPS reverse proxy
        cookie.setPath("/api/v1/stream");
        cookie.setMaxAge(STREAM_TOKEN_TTL_SECONDS);
        response.addCookie(cookie);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/{fileId}/playback")
    @PreAuthorize("hasAuthority('media:play')")
    public ApiResponse<java.util.Map<String, String>> playbackInfo(@PathVariable Integer fileId) {
        HlsStreamingService.PlaybackInfo info = hlsStreamingService.resolvePlaybackInfo(fileId);
        return ApiResponse.success(java.util.Map.of("mode", info.mode(), "url", info.url()));
    }

    @GetMapping("/{fileId}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Object> streamMedia(
            @PathVariable Integer fileId,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = streamService.getMediaResource(fileId);
        List<org.springframework.http.HttpRange> ranges = headers.getRange();
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(resource.contentLength())
                    .body(resource);
        }

        ResourceRegion region = streamService.getResourceRegion(resource, headers);
        long total = resource.contentLength();
        long start = region.getPosition();
        long end = Math.min(total - 1, start + region.getCount() - 1);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total)
                .contentLength(region.getCount())
                .body(region);
    }
    
    @GetMapping("/raw/{fileId}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> getRawFile(@PathVariable Integer fileId) throws IOException {
        Resource resource = streamService.getMediaResource(fileId);
        org.springframework.http.ContentDisposition contentDisposition = org.springframework.http.ContentDisposition.inline()
                .filename(resource.getFilename() != null ? resource.getFilename() : "raw", java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .headers(headers -> headers.setContentDisposition(contentDisposition))
                .body(resource);
    }

    @GetMapping("/images/{fileId}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> getImage(
            @PathVariable Integer fileId,
            @RequestParam(required = false) Integer w) throws IOException {
        return streamService.getImageResource(fileId, w);
    }

    @GetMapping("/{fileId}/transcode-speed")
    @PreAuthorize("hasAuthority('media:play')")
    public ApiResponse<HlsStreamingService.TranscodeSpeedInfo> transcodeSpeed(@PathVariable Integer fileId) {
        return ApiResponse.success(hlsStreamingService.getTranscodeSpeed(fileId));
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
