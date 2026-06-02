package com.mediamanager.metadata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.entity.Episode;
import com.mediamanager.metadata.entity.Season;
import com.mediamanager.metadata.entity.TvShowMetadata;
import com.mediamanager.metadata.repository.SeasonRepository;
import com.mediamanager.metadata.repository.TvShowMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TvSeasonSyncService {

    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    private static final int MAX_SEASONS = 30;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TvShowMetadataRepository tvShowMetadataRepository;
    private final SeasonRepository seasonRepository;

    @Transactional
    public void syncFromTmdb(MediaItem item, String apiKey, String tmdbTvId, String language) {
        if (item == null || !"TV_SHOW".equals(item.getType()) || tmdbTvId == null || tmdbTvId.isBlank()) {
            return;
        }
        String lang = language != null ? language : "zh-CN";
        try {
            String detailUrl = UriComponentsBuilder.fromHttpUrl(TMDB_BASE_URL)
                    .path("/tv/" + tmdbTvId)
                    .queryParam("api_key", apiKey)
                    .queryParam("language", lang)
                    .build()
                    .toUriString();
            JsonNode showNode = objectMapper.readTree(restTemplate.getForObject(detailUrl, String.class));
            int seasonCount = showNode.path("number_of_seasons").asInt(0);
            if (seasonCount <= 0) {
                return;
            }

            TvShowMetadata tvMeta = tvShowMetadataRepository.findByMediaItemId(item.getId())
                    .orElseGet(() -> {
                        TvShowMetadata created = TvShowMetadata.builder().mediaItem(item).build();
                        return tvShowMetadataRepository.save(created);
                    });

            List<Season> existing = seasonRepository.findByTvShowMetadata_MediaItem_IdOrderBySeasonNumberAsc(item.getId());
            if (!existing.isEmpty()) {
                seasonRepository.deleteAll(existing);
            }

            int limit = Math.min(seasonCount, MAX_SEASONS);
            List<Season> toSave = new ArrayList<>();
            for (int seasonNum = 1; seasonNum <= limit; seasonNum++) {
                Season season = fetchSeason(apiKey, tmdbTvId, seasonNum, lang, tvMeta);
                if (season != null) {
                    toSave.add(season);
                }
            }
            seasonRepository.saveAll(toSave);
            log.info("Synced {} TMDb seasons for TV item {}", toSave.size(), item.getId());
        } catch (Exception e) {
            log.warn("TMDb season sync failed for item {}: {}", item.getId(), e.getMessage());
        }
    }

    private Season fetchSeason(String apiKey, String tmdbTvId, int seasonNum, String lang, TvShowMetadata tvMeta) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(TMDB_BASE_URL)
                    .path("/tv/" + tmdbTvId + "/season/" + seasonNum)
                    .queryParam("api_key", apiKey)
                    .queryParam("language", lang)
                    .build()
                    .toUriString();
            JsonNode node = objectMapper.readTree(restTemplate.getForObject(url, String.class));
            Season season = Season.builder()
                    .tvShowMetadata(tvMeta)
                    .seasonNumber(node.path("season_number").asInt(seasonNum))
                    .name(node.path("name").asText(null))
                    .overview(node.path("overview").asText(null))
                    .build();
            String poster = node.path("poster_path").asText(null);
            if (poster != null && !poster.isBlank()) {
                season.setPosterPath(IMAGE_BASE_URL + poster);
            }
            List<Episode> episodes = new ArrayList<>();
            for (JsonNode epNode : node.path("episodes")) {
                Episode episode = Episode.builder()
                        .season(season)
                        .episodeNumber(epNode.path("episode_number").asInt())
                        .title(epNode.path("name").asText(null))
                        .overview(epNode.path("overview").asText(null))
                        .runtimeMinutes(epNode.path("runtime").asInt(0) > 0 ? epNode.path("runtime").asInt() : null)
                        .build();
                double vote = epNode.path("vote_average").asDouble(0.0);
                if (vote > 0) {
                    episode.setRating(vote);
                }
                String airDate = epNode.path("air_date").asText(null);
                if (airDate != null && !airDate.isBlank()) {
                    episode.setAirDate(LocalDate.parse(airDate));
                }
                episodes.add(episode);
            }
            season.setEpisodes(episodes);
            return season;
        } catch (Exception e) {
            log.debug("Skip TMDb season {} for show {}: {}", seasonNum, tmdbTvId, e.getMessage());
            return null;
        }
    }
}
