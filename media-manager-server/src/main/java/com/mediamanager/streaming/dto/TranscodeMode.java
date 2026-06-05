package com.mediamanager.streaming.dto;

import java.util.Arrays;
import java.util.Locale;

public enum TranscodeMode {
    AUTO("auto", "Auto"),
    SOFTWARE("software", "Software"),
    HARDWARE("hardware", "Hardware");

    private final String value;
    private final String label;

    TranscodeMode(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static TranscodeMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("soft".equals(normalized) || "cpu".equals(normalized)) {
            return SOFTWARE;
        }
        if ("hard".equals(normalized) || "hw".equals(normalized) || "gpu".equals(normalized)) {
            return HARDWARE;
        }
        return Arrays.stream(values())
                .filter(mode -> mode.value.equals(normalized))
                .findFirst()
                .orElse(AUTO);
    }
}
