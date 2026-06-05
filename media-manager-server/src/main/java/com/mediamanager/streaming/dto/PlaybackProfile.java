package com.mediamanager.streaming.dto;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

public record PlaybackProfile(PlaybackQuality quality, TranscodeMode transcodeMode) {

    private static final Pattern SAFE_VARIANT = Pattern.compile("[a-z0-9-]{3,48}");

    public PlaybackProfile {
        quality = quality == null ? PlaybackQuality.AUTO : quality;
        transcodeMode = transcodeMode == null ? TranscodeMode.AUTO : transcodeMode;
    }

    public static PlaybackProfile of(String quality, String transcodeMode) {
        return new PlaybackProfile(PlaybackQuality.from(quality), TranscodeMode.from(transcodeMode));
    }

    public static PlaybackProfile fromVariant(String variant) {
        if (variant == null || variant.isBlank()) {
            return new PlaybackProfile(PlaybackQuality.AUTO, TranscodeMode.AUTO);
        }
        String normalized = variant.trim().toLowerCase(Locale.ROOT);
        if (!SAFE_VARIANT.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Invalid HLS variant");
        }

        String[] parts = normalized.split("-", 2);
        if (parts.length == 1) {
            return new PlaybackProfile(PlaybackQuality.from(parts[0]), TranscodeMode.AUTO);
        }
        return new PlaybackProfile(PlaybackQuality.from(parts[1]), TranscodeMode.from(parts[0]));
    }

    public String variantKey() {
        return transcodeMode.value() + "-" + quality.value();
    }

    public PlaybackQuality effectiveQuality() {
        return quality == PlaybackQuality.AUTO ? PlaybackQuality.SOURCE : quality;
    }
}
