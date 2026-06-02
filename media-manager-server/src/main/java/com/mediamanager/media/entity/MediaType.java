package com.mediamanager.media.entity;

/**
 * Canonical media type enum. Intended to eventually replace string-based type comparisons
 * throughout the codebase. For now, used as a reference and in new code paths.
 */
public enum MediaType {
    MOVIE,
    TV_SHOW,
    EPISODE,
    IMAGE,
    AUDIO,
    MIXED
}
