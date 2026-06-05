package com.mediamanager.classification.spi;

import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.plugin.MediaManagerPlugin;
import com.mediamanager.plugin.PluginKind;

import java.util.List;

public interface ClassifierStrategy extends MediaManagerPlugin {

    @Override
    default PluginKind kind() {
        return PluginKind.CLASSIFIER;
    }

    @Override
    default String id() {
        return getClass().getSimpleName().replace("Classifier", "").toLowerCase();
    }

    @Override
    default String displayName() {
        return getClass().getSimpleName();
    }

    @Override
    default int defaultPriority() {
        return getPriority();
    }

    /**
     * Executes classification rules on a given MediaItem.
     * Often relies on the metadata populated in M3.
     */
    void classify(MediaItem item);

    default void classifyBatch(List<MediaItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MediaItem item : items) {
            classify(item);
        }
    }

    /**
     * Database-only classifiers can run in a short transaction. Classifiers that
     * call external services should opt out so they do not hold SQLite snapshots
     * while waiting on network IO.
     */
    default boolean runsInTransaction() {
        return true;
    }

    /**
     * Priority of this classifier. Lower number = earlier execution.
     */
    int getPriority();
}
