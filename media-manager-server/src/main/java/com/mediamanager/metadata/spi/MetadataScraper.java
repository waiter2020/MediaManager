package com.mediamanager.metadata.spi;

import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.plugin.MediaManagerPlugin;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;

import java.util.List;
import java.util.Map;

public interface MetadataScraper extends MediaManagerPlugin {

    @Override
    default PluginKind kind() {
        return PluginKind.SCRAPER;
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
     * Unique identifier for this scraper type (e.g. "TMDB", "JAVBUS", "STASHDB").
     */
    String getType();

    /**
     * Scrapes metadata remotely for a given MediaItem and primary MediaFile.
     */
    MetadataResult scrape(ScrapeContext context, LibraryPluginConfig config);

    /**
     * Searches for metadata candidates remotely for manual match.
     */
    List<Map<String, Object>> searchCandidates(String query, LibraryPluginConfig config, String mediaType, String language);

    /**
     * Fetches metadata details by external provider ID.
     */
    MetadataResult fetchByExternalId(String externalId, LibraryPluginConfig config, String mediaType, String language);

    /**
     * Context passed to each scraper in the chain.
     */
    record ScrapeContext(
            MediaItem mediaItem,
            MediaFile primaryFile,
            MetadataResult currentAccumulatedResult
    ) {}
}
