package com.mediamanager.streaming.dto;

import java.util.List;

public record PlaybackInfoResponse(
        String mode,
        String playMethod,
        String url,
        String variant,
        String quality,
        String transcodeMode,
        boolean directPlayable,
        boolean transcoding,
        String container,
        String videoCodec,
        String audioCodec,
        Integer width,
        Integer height,
        Integer bitrate,
        List<PlaybackOption> qualities,
        List<PlaybackOption> transcodeModes,
        List<String> transcodingReasons
) {
    public record PlaybackOption(
            String value,
            String label,
            Integer height,
            Integer bitrateKbps
    ) {}
}
