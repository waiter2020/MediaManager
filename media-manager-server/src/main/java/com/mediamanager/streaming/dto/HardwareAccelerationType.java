package com.mediamanager.streaming.dto;

import java.util.Arrays;
import java.util.Locale;

public enum HardwareAccelerationType {
    NONE("none", "None"),
    NVENC("nvenc", "NVIDIA NVENC"),
    QSV("qsv", "Intel QSV"),
    VAAPI("vaapi", "VA-API"),
    AMF("amf", "AMD AMF"),
    AUTO("auto", "Auto");

    private final String value;
    private final String label;

    HardwareAccelerationType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    public static HardwareAccelerationType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.value.equals(normalized))
                .findFirst()
                .orElse(AUTO);
    }
}
