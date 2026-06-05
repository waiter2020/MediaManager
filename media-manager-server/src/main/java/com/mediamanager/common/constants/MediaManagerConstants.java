package com.mediamanager.common.constants;

import java.util.Set;

/**
 * Centralized constants for the MediaManager application.
 */
public final class MediaManagerConstants {

    private MediaManagerConstants() {
    }

    // ── Application ──────────────────────────────────────────────
    public static final String VERSION = "2.0.0";

    // ── AI Model Defaults ────────────────────────────────────────
    public static final String DEFAULT_OLLAMA_LLM_MODEL = "qwen2.5:7b";
    public static final String DEFAULT_OLLAMA_EMBED_MODEL = "nomic-embed-text";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";

    // ── Thumbnails ───────────────────────────────────────────────
    public static final int THUMBNAIL_WIDTH = 480;
    public static final int THUMBNAIL_PREVIEW_WIDTH = 320;
    public static final int THUMBNAIL_SEEK_PRIMARY = 10;
    public static final int THUMBNAIL_SEEK_FALLBACK = 1;

    // ── Pagination & Batch ───────────────────────────────────────
    public static final int MAX_BATCH_SIZE = 500;
    public static final int DEFAULT_PAGE_SIZE = 20;

    // ── File Extension Sets ──────────────────────────────────────
    public static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v",
            "mpg", "mpeg", "ts", "vob", "3gp", "ogv", "rmvb"
    );

    public static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "wav", "aac", "ogg", "wma", "m4a",
            "opus", "alac", "aiff", "ape", "dsf", "dff"
    );

    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            "tiff", "tif", "ico", "heic", "heif", "avif"
    );
}
