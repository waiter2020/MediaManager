package com.mediamanager.metadata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.service.ThumbnailService;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.TvShowMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.TvShowMetadataRepository;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MetadataApplyService {

    private final MediaItemRepository mediaItemRepository;
    private final MovieMetadataRepository movieMetadataRepository;
    private final TvShowMetadataRepository tvShowMetadataRepository;
    private final ThumbnailService thumbnailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void applyResult(MediaItem item, MetadataResult result, MediaFile primaryFile) {
        if (result == null) {
            return;
        }
        if (result.getTitle() != null) {
            item.setTitle(result.getTitle());
        }
        if (result.getOriginalTitle() != null) {
            item.setOriginalTitle(result.getOriginalTitle());
        }
        if (result.getReleaseDate() != null) {
            item.setReleaseDate(result.getReleaseDate());
        }
        if (result.getOverview() != null) {
            item.setOverview(result.getOverview());
        }
        if (result.getRating() != null) {
            item.setRating(BigDecimal.valueOf(result.getRating()));
        }
        if (result.getPosterPath() != null) {
            item.setPosterPath(result.getPosterPath());
        }
        if (result.getBackdropPath() != null) {
            item.setBackdropPath(result.getBackdropPath());
        }
        if (result.getProviderIds() != null && !result.getProviderIds().isEmpty()) {
            item.setProviderIds(toJson(result.getProviderIds()));
        }

        if (item.getPosterPath() == null
                && ("MOVIE".equals(item.getType()) || "TV_SHOW".equals(item.getType()))
                && primaryFile != null) {
            String thumbnailPath = thumbnailService.generateThumbnail(item.getId(), primaryFile.getFilePath());
            if (thumbnailPath != null) {
                item.setPosterPath(thumbnailPath);
            }
        }

        boolean hasMetadata = result.getTitle() != null || result.getOverview() != null || result.getPosterPath() != null;
        if (hasMetadata) {
            item.setStatus("IDENTIFIED");
        }
        item.setLastScannedAt(Instant.now());
        mediaItemRepository.save(item);
        persistSpecificMetadata(item, result);
    }

    private void persistSpecificMetadata(MediaItem item, MetadataResult result) {
        if ("MOVIE".equals(item.getType())) {
            MovieMetadata mm = movieMetadataRepository.findByMediaItemId(item.getId())
                    .orElse(MovieMetadata.builder().mediaItem(item).build());
            if (result.getRuntimeMinutes() != null) {
                mm.setRuntimeMinutes(result.getRuntimeMinutes());
            }
            if (result.getCertification() != null) {
                mm.setCertification(result.getCertification());
            }
            if (result.getGenres() != null) {
                mm.setGenres(toJson(result.getGenres()));
            }
            if (result.getCastInfo() != null) {
                mm.setCastInfo(result.getCastInfo());
            }
            if (result.getStudios() != null) {
                mm.setStudios(toJson(result.getStudios()));
            }
            movieMetadataRepository.save(mm);
        } else if ("TV_SHOW".equals(item.getType())) {
            TvShowMetadata tm = tvShowMetadataRepository.findByMediaItemId(item.getId())
                    .orElse(TvShowMetadata.builder().mediaItem(item).build());
            if (result.getNetwork() != null) {
                tm.setNetwork(result.getNetwork());
            }
            if (result.getStatus() != null) {
                tm.setStatus(result.getStatus());
            }
            if (result.getGenres() != null) {
                tm.setGenres(toJson(result.getGenres()));
            }
            if (result.getCastInfo() != null) {
                tm.setCastInfo(result.getCastInfo());
            }
            tvShowMetadataRepository.save(tm);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
