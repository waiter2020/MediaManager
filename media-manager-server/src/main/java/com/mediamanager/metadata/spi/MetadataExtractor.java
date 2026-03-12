package com.mediamanager.metadata.spi;

import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;

public interface MetadataExtractor {

    /**
     * Unique identifier for this extractor type (e.g. "NFO", "TMDB", "FFPROBE").
     * Must match the type in LibraryExtractorConfig.
     */
    String getType();

    /**
     * Extracts metadata for a given MediaItem and its primary MediaFile.
     * 
     * @param context Immutable context containing the item, file, and current accumulated result.
     * @param config The specific configuration for this extractor for this library.
     * @return Extracted metadata to be merged into the final result, or null if nothing was found.
     */
    MetadataResult extract(ExtractorContext context, LibraryExtractorConfig config);

    /**
     * Context passed to each extractor in the chain.
     */
    record ExtractorContext(
            MediaItem mediaItem,
            MediaFile primaryFile,
            MetadataResult currentAccumulatedResult
    ) {}
}
