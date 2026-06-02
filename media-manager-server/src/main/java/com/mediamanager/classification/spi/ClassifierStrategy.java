package com.mediamanager.classification.spi;

import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.plugin.MediaManagerPlugin;
import com.mediamanager.plugin.PluginKind;

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

    /**
     * Priority of this classifier. Lower number = earlier execution.
     */
    int getPriority();
}
