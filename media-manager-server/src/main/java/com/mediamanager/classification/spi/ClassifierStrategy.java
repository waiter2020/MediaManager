package com.mediamanager.classification.spi;

import com.mediamanager.media.entity.MediaItem;

public interface ClassifierStrategy {

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
