package com.mediamanager.metadata.spi;

import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.plugin.MediaManagerPlugin;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;

public interface MetadataExtractor extends MediaManagerPlugin {

    @Override
    default PluginKind kind() {
        return PluginKind.EXTRACTOR;
    }

    @Override
    default String id() {
        return getType().toLowerCase();
    }

    @Override
    default String displayName() {
        return getType();
    }

    /**
     * Unique identifier for this extractor type (e.g. "NFO", "FFPROBE").
     */
    String getType();

    /**
     * Extracts metadata for a given MediaItem and its primary MediaFile.
     * 
     * @param context Immutable context containing the item, file, and current accumulated result.
     * @param config The specific configuration for this extractor for this library.
     * @return Extracted metadata to be merged into the final result, or null if nothing was found.
     */
    MetadataResult extract(ExtractorContext context, LibraryPluginConfig config);

    /**
     * Context passed to each extractor in the chain.
     */
    record ExtractorContext(
            MediaItem mediaItem,
            MediaFile primaryFile,
            MetadataResult currentAccumulatedResult
    ) {}
}
