package com.mediamanager.streaming.dto;

import com.mediamanager.media.entity.MediaFile;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum PlaybackQuality {
    AUTO("auto", "Auto", null, null),
    SOURCE("source", "Source", null, null),
    P2160("2160p", "2160p", 2160, 16000),
    P1080("1080p", "1080p", 1080, 8000),
    P720("720p", "720p", 720, 4000),
    P480("480p", "480p", 480, 1800),
    P360("360p", "360p", 360, 900);

    private final String value;
    private final String label;
    private final Integer maxHeight;
    private final Integer videoBitrateKbps;

    PlaybackQuality(String value, String label, Integer maxHeight, Integer videoBitrateKbps) {
        this.value = value;
        this.label = label;
        this.maxHeight = maxHeight;
        this.videoBitrateKbps = videoBitrateKbps;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public Integer maxHeight() {
        return maxHeight;
    }

    public Integer videoBitrateKbps() {
        return videoBitrateKbps;
    }

    public boolean constrained() {
        return maxHeight != null;
    }

    public static PlaybackQuality from(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("original".equals(normalized) || "source".equals(normalized) || "direct".equals(normalized)) {
            return SOURCE;
        }
        if ("4k".equals(normalized) || "uhd".equals(normalized)) {
            return P2160;
        }
        return Arrays.stream(values())
                .filter(quality -> quality.value.equals(normalized))
                .findFirst()
                .orElse(AUTO);
    }

    public static List<PlaybackQuality> optionsFor(MediaFile file) {
        Integer sourceHeight = file != null ? file.getHeight() : null;
        return Arrays.stream(values())
                .filter(quality -> !quality.constrained()
                        || sourceHeight == null
                        || sourceHeight <= 0
                        || quality.maxHeight <= sourceHeight)
                .toList();
    }
}
