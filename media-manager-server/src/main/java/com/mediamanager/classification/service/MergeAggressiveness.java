package com.mediamanager.classification.service;

public enum MergeAggressiveness {
    CONSERVATIVE,
    STANDARD,
    AGGRESSIVE;

    public static MergeAggressiveness from(String value) {
        if (value == null || value.isBlank()) {
            return AGGRESSIVE;
        }
        return switch (value.trim().toLowerCase()) {
            case "conservative" -> CONSERVATIVE;
            case "standard" -> STANDARD;
            default -> AGGRESSIVE;
        };
    }
}
