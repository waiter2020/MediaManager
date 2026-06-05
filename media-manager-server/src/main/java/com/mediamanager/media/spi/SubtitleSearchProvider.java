package com.mediamanager.media.spi;

import com.mediamanager.media.dto.SubtitleSearchResultDto;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;

import java.util.List;

public interface SubtitleSearchProvider {

    String id();

    List<SubtitleSearchResultDto> search(SearchContext context);

    record SearchContext(
            MediaItem mediaItem,
            MediaFile primaryFile,
            String query,
            String language
    ) {}
}
