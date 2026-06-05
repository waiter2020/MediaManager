package com.mediamanager.metadata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.AudioMetadata;
import com.mediamanager.metadata.entity.Episode;
import com.mediamanager.metadata.entity.ImageMetadata;
import com.mediamanager.metadata.entity.Season;
import com.mediamanager.metadata.entity.TvShowMetadata;
import com.mediamanager.metadata.repository.AudioMetadataRepository;
import com.mediamanager.metadata.repository.ImageMetadataRepository;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.SeasonRepository;
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
    private final MediaFileRepository mediaFileRepository;
    private final MovieMetadataRepository movieMetadataRepository;
    private final TvShowMetadataRepository tvShowMetadataRepository;
    private final SeasonRepository seasonRepository;
    private final ImageMetadataRepository imageMetadataRepository;
    private final AudioMetadataRepository audioMetadataRepository;
    private final ObjectMapper objectMapper;
    private final NfoExportService nfoExportService;

    @Transactional
    public void applyResult(Integer itemId, MetadataResult result, Integer primaryFileId) {
        if (itemId == null) {
            return;
        }
        MediaItem item = mediaItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return;
        }
        MediaFile primaryFile = primaryFileId != null
                ? mediaFileRepository.findById(primaryFileId).orElse(null)
                : null;
        applyResultToItem(item, result, primaryFile);
    }

    @Transactional
    public void applyResult(MediaItem item, MetadataResult result, MediaFile primaryFile) {
        applyResultToItem(item, result, primaryFile);
    }

    private void applyResultToItem(MediaItem item, MetadataResult result, MediaFile primaryFile) {
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

        boolean hasMetadata = result.getTitle() != null || result.getOverview() != null || result.getPosterPath() != null;
        if (hasMetadata) {
            item.setStatus("IDENTIFIED");
        }
        item.setLastScannedAt(Instant.now());
        MediaItem saved = mediaItemRepository.save(item);
        persistSpecificMetadata(saved, result, primaryFile);

        // Export NFO automatically after metadata is applied
        nfoExportService.export(saved);
    }

    private void persistSpecificMetadata(MediaItem item, MetadataResult result, MediaFile primaryFile) {
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
            persistLocalEpisode(item, result, primaryFile);
        } else if ("EPISODE".equals(item.getType())) {
            persistEpisodeUnderShowShell(item, result, primaryFile);
        } else if ("IMAGE".equals(item.getType())) {
            ImageMetadata im = imageMetadataRepository.findByMediaItemId(item.getId())
                    .orElse(ImageMetadata.builder().mediaItem(item).build());
            if (result.getWidth() != null) im.setWidth(result.getWidth());
            if (result.getHeight() != null) im.setHeight(result.getHeight());
            if (result.getCameraMake() != null) im.setCameraMake(result.getCameraMake());
            if (result.getCameraModel() != null) im.setCameraModel(result.getCameraModel());
            if (result.getLens() != null) im.setLens(result.getLens());
            if (result.getIso() != null) im.setIso(result.getIso());
            if (result.getAperture() != null) im.setAperture(result.getAperture());
            if (result.getShutterSpeed() != null) im.setShutterSpeed(result.getShutterSpeed());
            if (result.getTakenAt() != null) im.setTakenAt(result.getTakenAt());
            if (result.getGpsLatitude() != null) im.setGpsLatitude(result.getGpsLatitude());
            if (result.getGpsLongitude() != null) im.setGpsLongitude(result.getGpsLongitude());
            if (result.getExifData() != null) im.setExifData(result.getExifData());
            imageMetadataRepository.save(im);
        } else if ("AUDIO".equals(item.getType())) {
            AudioMetadata am = audioMetadataRepository.findByMediaItemId(item.getId())
                    .orElse(AudioMetadata.builder().mediaItem(item).build());
            if (result.getArtist() != null) am.setArtist(result.getArtist());
            if (result.getAlbum() != null) am.setAlbum(result.getAlbum());
            if (result.getAlbumArtist() != null) am.setAlbumArtist(result.getAlbumArtist());
            if (result.getTrackNumber() != null) am.setTrackNumber(result.getTrackNumber());
            if (result.getDiscNumber() != null) am.setDiscNumber(result.getDiscNumber());
            if (result.getGenres() != null) am.setGenres(toJson(result.getGenres()));
            if (result.getDurationSeconds() != null) am.setDurationSeconds(result.getDurationSeconds());
            if (result.getBitrate() != null) am.setBitrate(result.getBitrate());
            if (result.getSampleRate() != null) am.setSampleRate(result.getSampleRate());
            if (result.getChannels() != null) am.setChannels(result.getChannels());
            audioMetadataRepository.save(am);
        }
    }

    private void persistEpisodeUnderShowShell(MediaItem episodeItem, MetadataResult result, MediaFile primaryFile) {
        if (primaryFile == null || result == null || result.getOriginalTitle() == null || result.getOriginalTitle().isBlank()) {
            return;
        }
        MediaItem show = mediaItemRepository
                .findTitleCandidates(episodeItem.getLibrary().getId(), "TV_SHOW", result.getOriginalTitle())
                .stream()
                .findFirst()
                .orElseGet(() -> mediaItemRepository.save(MediaItem.builder()
                        .library(episodeItem.getLibrary())
                        .type("TV_SHOW")
                        .title(result.getOriginalTitle())
                        .originalTitle(result.getOriginalTitle())
                        .status("UNIDENTIFIED")
                        .hidden(false)
                        .build()));
        persistLocalEpisode(show, result, primaryFile);
    }

    private void persistLocalEpisode(MediaItem item, MetadataResult result, MediaFile primaryFile) {
        if (primaryFile == null || result.getSeasonNumber() == null || result.getEpisodeNumber() == null) {
            return;
        }
        TvShowMetadata tvMeta = tvShowMetadataRepository.findByMediaItemId(item.getId())
                .orElseGet(() -> tvShowMetadataRepository.save(TvShowMetadata.builder().mediaItem(item).build()));
        Season season = seasonRepository
                .findByTvShowMetadata_MediaItem_IdAndSeasonNumber(item.getId(), result.getSeasonNumber())
                .orElseGet(() -> seasonRepository.save(Season.builder()
                        .tvShowMetadata(tvMeta)
                        .seasonNumber(result.getSeasonNumber())
                        .name("Season " + result.getSeasonNumber())
                        .build()));

        Episode episode = season.getEpisodes().stream()
                .filter(ep -> ep.getMediaFile() != null && ep.getMediaFile().getId().equals(primaryFile.getId()))
                .findFirst()
                .or(() -> season.getEpisodes().stream()
                        .filter(ep -> ep.getMediaFile() == null
                                && result.getEpisodeNumber().equals(ep.getEpisodeNumber()))
                        .findFirst())
                .orElseGet(() -> {
                    Episode created = Episode.builder()
                            .season(season)
                            .mediaFile(primaryFile)
                            .build();
                    season.getEpisodes().add(created);
                    return created;
                });
        if (episode.getMediaFile() == null) {
            episode.setMediaFile(primaryFile);
        }
        episode.setEpisodeNumber(result.getEpisodeNumber());
        if (result.getTitle() != null) {
            episode.setTitle(result.getTitle());
        }
        if (result.getOverview() != null) {
            episode.setOverview(result.getOverview());
        }
        if (result.getReleaseDate() != null) {
            episode.setAirDate(result.getReleaseDate());
        }
        if (result.getRuntimeMinutes() != null) {
            episode.setRuntimeMinutes(result.getRuntimeMinutes());
        }
        if (result.getRating() != null) {
            episode.setRating(result.getRating());
        }
        seasonRepository.save(season);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
