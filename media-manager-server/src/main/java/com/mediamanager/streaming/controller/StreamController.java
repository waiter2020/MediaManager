package com.mediamanager.streaming.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.common.security.JwtTokenProvider;
import com.mediamanager.media.service.MediaSubtitleService;
import com.mediamanager.streaming.dto.PlaybackInfoResponse;
import com.mediamanager.streaming.dto.PlaybackProfile;
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
import org.springframework.http.HttpRange;
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
    private final MediaSubtitleService subtitleService;

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
    public ApiResponse<PlaybackInfoResponse> playbackInfo(
            @PathVariable Integer fileId,
            @RequestParam(required = false, defaultValue = "auto") String mode,
            @RequestParam(required = false, defaultValue = "auto") String quality,
            @RequestParam(required = false, defaultValue = "auto") String transcodeMode,
            @RequestParam(required = false) Double start) {
        return ApiResponse.success(hlsStreamingService.resolvePlaybackInfo(fileId, mode, quality, transcodeMode, start));
    }

    @GetMapping(value = "/{fileId}", headers = "!" + HttpHeaders.RANGE)
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> streamMedia(@PathVariable Integer fileId) throws IOException {

        Resource resource = streamService.getMediaResource(fileId);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @GetMapping(value = "/{fileId}", headers = HttpHeaders.RANGE)
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<ResourceRegion> streamMediaRange(
            @PathVariable Integer fileId,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = streamService.getMediaResource(fileId);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        long total = resource.contentLength();
        List<HttpRange> ranges;
        try {
            ranges = headers.getRange();
        } catch (IllegalArgumentException e) {
            return rangeNotSatisfiable(total);
        }
        if (ranges.isEmpty()) {
            return rangeNotSatisfiable(total);
        }

        ResourceRegion region;
        try {
            region = streamService.getResourceRegion(resource, ranges.get(0), total);
        } catch (IllegalArgumentException e) {
            return rangeNotSatisfiable(total);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private ResponseEntity<ResourceRegion> rangeNotSatisfiable(long total) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + total)
                .build();
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

    @GetMapping("/subtitles/{subtitleId}.vtt")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> getSubtitleTrack(@PathVariable Integer subtitleId) throws IOException {
        return subtitleService.getSubtitleTrack(subtitleId);
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
    public ApiResponse<HlsStreamingService.TranscodeSpeedInfo> transcodeSpeed(
            @PathVariable Integer fileId,
            @RequestParam(required = false) String variant) {
        return ApiResponse.success(hlsStreamingService.getTranscodeSpeed(fileId, variant));
    }

    @PostMapping("/{fileId}/transcode/stop")
    @PreAuthorize("hasAuthority('media:play')")
    public ApiResponse<Void> stopTranscode(@PathVariable Integer fileId) {
        hlsStreamingService.stopTranscode(fileId);
        return ApiResponse.success();
    }

    @GetMapping("/{fileId}/hls/master.m3u8")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> hlsMaster(
            @PathVariable Integer fileId,
            @RequestParam(required = false, defaultValue = "auto") String quality,
            @RequestParam(required = false, defaultValue = "auto") String transcodeMode) {
        return hlsPlaylistResponse(hlsStreamingService.getMasterPlaylist(
                fileId, PlaybackProfile.of(quality, transcodeMode)));
    }

    @GetMapping("/{fileId}/hls/{variant}/master.m3u8")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> hlsMasterVariant(
            @PathVariable Integer fileId,
            @PathVariable String variant,
            @RequestParam(required = false) Double start) {
        return hlsPlaylistResponse(hlsStreamingService.getMasterPlaylist(
                fileId, PlaybackProfile.fromVariant(variant), start));
    }

    @GetMapping("/{fileId}/hls/{segment}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> hlsSegment(
            @PathVariable Integer fileId,
            @PathVariable String segment) {
        return hlsSegmentResponse(hlsStreamingService.getSegment(fileId, segment), segment);
    }

    @GetMapping("/{fileId}/hls/{variant}/{segment}")
    @PreAuthorize("hasAuthority('media:play')")
    public ResponseEntity<Resource> hlsSegmentVariant(
            @PathVariable Integer fileId,
            @PathVariable String variant,
            @PathVariable String segment) {
        return hlsSegmentResponse(hlsStreamingService.getSegment(fileId, variant, segment), segment);
    }

    private ResponseEntity<Resource> hlsPlaylistResponse(Resource resource) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl")
                .body(resource);
    }

    private ResponseEntity<Resource> hlsSegmentResponse(Resource resource, String segment) {
        String contentType = segment.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }
}
