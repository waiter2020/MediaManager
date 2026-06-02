package com.mediamanager.media.service;

import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.response.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.media.dto.MediaItemDetailResponse;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.dto.MediaFileDto;
import com.mediamanager.media.dto.TagDto;
import com.mediamanager.media.dto.CategoryDto;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaItemSpecification;
import com.mediamanager.metadata.dto.MovieMetadataDto;
import com.mediamanager.metadata.dto.TvShowMetadataDto;
import com.mediamanager.metadata.dto.SeasonDto;
import com.mediamanager.metadata.dto.EpisodeDto;
import com.mediamanager.metadata.dto.ImageMetadataDto;
import com.mediamanager.metadata.dto.AudioMetadataDto;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.TvShowMetadata;
import com.mediamanager.metadata.entity.Season;
import com.mediamanager.metadata.entity.Episode;
import com.mediamanager.metadata.entity.ImageMetadata;
import com.mediamanager.metadata.entity.AudioMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.TvShowMetadataRepository;
import com.mediamanager.metadata.repository.SeasonRepository;
import com.mediamanager.metadata.repository.ImageMetadataRepository;
import com.mediamanager.metadata.repository.AudioMetadataRepository;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.metadata.service.MetadataPipelineService;
import com.mediamanager.metadata.service.MetadataApplyService;
import com.mediamanager.metadata.service.TmdbClientService;
import com.mediamanager.metadata.spi.MetadataScraper;
import com.mediamanager.plugin.PluginRegistry;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.metadata.service.TvSeasonSyncService;
import com.mediamanager.metadata.service.NfoExportService;
import com.mediamanager.plugin.service.LibraryPluginConfigResolver;
import com.mediamanager.metadata.dto.IdentifyRequest;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.util.FileNameParser;
import com.mediamanager.classification.service.ClassificationEngine;
import com.mediamanager.system.service.LibraryAccessService;
import com.mediamanager.common.service.StoragePathMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaItemService {

    private final MediaItemRepository itemRepository;
    private final MediaFileRepository fileRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final MetadataPipelineService metadataPipelineService;
    private final ThumbnailService thumbnailService;
    private final MovieMetadataRepository movieMetadataRepository;
    private final TvShowMetadataRepository tvShowMetadataRepository;
    private final SeasonRepository seasonRepository;
    private final ImageMetadataRepository imageMetadataRepository;
    private final AudioMetadataRepository audioMetadataRepository;
    private final ObjectMapper objectMapper;
    private final LibraryAccessService libraryAccessService;
    private final TmdbClientService tmdbClientService;
    private final MetadataApplyService metadataApplyService;
    private final MediaPostProcessService mediaPostProcessService;
    private final ClassificationEngine classificationEngine;
    private final FileNameParser fileNameParser;
    private final PluginRegistry pluginRegistry;
    private final MediaLibraryRepository libraryRepository;
    private final LibraryPluginConfigResolver pluginConfigResolver;
    private final TvSeasonSyncService tvSeasonSyncService;
    private final NfoExportService nfoExportService;
    private final StoragePathMapper storagePathMapper;
    private final MediaFileLifecycleService mediaFileLifecycleService;

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> getItems(
            Integer libraryId,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            Integer minYear,
            Integer maxYear,
            Double minRating,
            int page,
            int size,
            String sortField,
            String sortOrder) {

        PageRequest pageRequest = PageRequest.of(page - 1, size, resolveSort(sortField, sortOrder));
        
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        Specification<MediaItem> spec = MediaItemSpecification.filterBy(
                libraryIds, type, keyword, categoryIds, tagIds, minYear, maxYear, minRating);
        
        Page<MediaItem> itemPage = itemRepository.findAll(spec, pageRequest);
        
        List<MediaItemResponse> responses = itemPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(responses, itemPage.getTotalElements(), page, size);
    }
    
    @Transactional(readOnly = true)
    public MediaItemResponse getItem(Integer id) {
        MediaItem item = requireVisibleItem(id);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public MediaItemDetailResponse getItemDetail(Integer id) {
        MediaItem item = requireVisibleItem(id);

        List<MediaFileDto> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .map(this::toFileDto)
                .collect(Collectors.toList());

        MovieMetadataDto movieMetadataDto = null;
        TvShowMetadataDto tvShowMetadataDto = null;
        ImageMetadataDto imageMetadataDto = null;
        AudioMetadataDto audioMetadataDto = null;

        if ("MOVIE".equals(item.getType())) {
            movieMetadataDto = movieMetadataRepository.findByMediaItemId(item.getId())
                    .map(this::toMovieMetadataDto)
                    .orElse(null);
        }
        if ("TV_SHOW".equals(item.getType())) {
            tvShowMetadataDto = tvShowMetadataRepository.findByMediaItemId(item.getId())
                    .map(this::toTvShowMetadataDto)
                    .orElse(null);
        }
        if ("IMAGE".equals(item.getType())) {
            imageMetadataDto = imageMetadataRepository.findByMediaItemId(item.getId())
                    .map(this::toImageMetadataDto)
                    .orElse(null);
        }
        if ("AUDIO".equals(item.getType())) {
            audioMetadataDto = audioMetadataRepository.findByMediaItemId(item.getId())
                    .map(this::toAudioMetadataDto)
                    .orElse(null);
        }

        List<TagDto> tags = item.getTags().stream()
                .map(this::toTagDto)
                .collect(Collectors.toList());
        List<CategoryDto> categories = item.getCategories().stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());

        Float rating = item.getRating() != null ? item.getRating().floatValue() : null;

        return MediaItemDetailResponse.builder()
                .id(item.getId())
                .libraryId(item.getLibrary().getId())
                .title(item.getTitle())
                .originalTitle(item.getOriginalTitle())
                .type(item.getType())
                .status(item.getStatus())
                .releaseDate(item.getReleaseDate())
                .rating(rating)
                .overview(item.getOverview())
                .posterPath(item.getPosterPath())
                .backdropPath(item.getBackdropPath())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .files(files)
                .tags(tags)
                .categories(categories)
                .movieMetadata(movieMetadataDto)
                .tvShowMetadata(tvShowMetadataDto)
                .imageMetadata(imageMetadataDto)
                .audioMetadata(audioMetadataDto)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SeasonDto> getItemSeasons(Integer id) {
        MediaItem item = requireVisibleItem(id);
        if (!"TV_SHOW".equals(item.getType())) {
            return List.of();
        }
        return seasonRepository.findByTvShowMetadata_MediaItem_IdOrderBySeasonNumberAsc(item.getId()).stream()
                .map(this::toSeasonDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> syncTvSeasons(Integer id) {
        MediaItem item = requireVisibleItem(id);
        if (!"TV_SHOW".equals(item.getType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Only TV_SHOW items support season sync");
        }
        String tmdbId = resolveTmdbProviderId(item);
        if (tmdbId == null || tmdbId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "No TMDb ID on item; use manual identify with TMDb first");
        }
        var library = libraryRepository.findWithDetailsById(item.getLibrary().getId()).orElse(item.getLibrary());
        String apiKey = tmdbClientService.resolveApiKeyForLibrary(library);
        tvSeasonSyncService.syncFromTmdb(item, apiKey, tmdbId, library.getLanguage());
        int seasonCount = seasonRepository.findByTvShowMetadata_MediaItem_IdOrderBySeasonNumberAsc(item.getId()).size();
        return Map.of("seasonCount", seasonCount, "tmdbId", tmdbId);
    }

    private String resolveTmdbProviderId(MediaItem item) {
        if (item.getProviderIds() == null || item.getProviderIds().isBlank()) {
            return null;
        }
        try {
            Map<String, String> map = objectMapper.readValue(item.getProviderIds(), new TypeReference<>() {});
            return map != null ? map.get("tmdb") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public MediaItemResponse toResponsePublic(MediaItem item) {
        return toResponse(item);
    }

    private MediaItemResponse toResponse(MediaItem item) {
        Float rating = item.getRating() != null ? item.getRating().floatValue() : null;

        List<Integer> fileIds = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .map(MediaFile::getId)
                .collect(Collectors.toList());

        return MediaItemResponse.builder()
                .id(item.getId())
                .libraryId(item.getLibrary().getId())
                .libraryName(item.getLibrary() != null ? item.getLibrary().getName() : null)
                .title(item.getTitle())
                .type(item.getType())
                .status(item.getStatus())
                .releaseDate(item.getReleaseDate())
                .rating(rating)
                .overview(item.getOverview())
                .posterPath(item.getPosterPath())
                .fileIds(fileIds)
                .build();
    }

    private MediaFileDto toFileDto(MediaFile file) {
        return MediaFileDto.builder()
                .id(file.getId())
                .filePath(file.getFilePath())
                .fileName(file.getFileName())
                .fileSize(file.getFileSize())
                .mimeType(file.getMimeType())
                .container(file.getContainer())
                .videoCodec(file.getVideoCodec())
                .audioCodec(file.getAudioCodec())
                .width(file.getWidth())
                .height(file.getHeight())
                .durationSeconds(file.getDurationSeconds())
                .bitrate(file.getBitrate())
                .build();
    }

    private MovieMetadataDto toMovieMetadataDto(MovieMetadata metadata) {
        return MovieMetadataDto.builder()
                .tagline(metadata.getTagline())
                .runtimeMinutes(metadata.getRuntimeMinutes())
                .certification(metadata.getCertification())
                .genres(readStringList(metadata.getGenres()))
                .studios(readStringList(metadata.getStudios()))
                .build();
    }

    private TvShowMetadataDto toTvShowMetadataDto(TvShowMetadata metadata) {
        return TvShowMetadataDto.builder()
                .status(metadata.getStatus())
                .network(metadata.getNetwork())
                .genres(readStringList(metadata.getGenres()))
                .build();
    }

    private SeasonDto toSeasonDto(Season season) {
        List<EpisodeDto> episodes = season.getEpisodes() == null ? List.of()
                : season.getEpisodes().stream().map(this::toEpisodeDto).collect(Collectors.toList());
        return SeasonDto.builder()
                .id(season.getId())
                .seasonNumber(season.getSeasonNumber())
                .name(season.getName())
                .overview(season.getOverview())
                .posterPath(season.getPosterPath())
                .episodes(episodes)
                .build();
    }

    private EpisodeDto toEpisodeDto(Episode episode) {
        Integer mediaFileId = episode.getMediaFile() != null ? episode.getMediaFile().getId() : null;
        Integer mediaItemId = episode.getMediaFile() != null && episode.getMediaFile().getMediaItem() != null
                ? episode.getMediaFile().getMediaItem().getId()
                : null;
        Float rating = episode.getRating() != null ? episode.getRating().floatValue() : null;
        return EpisodeDto.builder()
                .id(episode.getId())
                .episodeNumber(episode.getEpisodeNumber())
                .title(episode.getTitle())
                .overview(episode.getOverview())
                .airDate(episode.getAirDate())
                .runtimeMinutes(episode.getRuntimeMinutes())
                .rating(rating)
                .mediaFileId(mediaFileId)
                .mediaItemId(mediaItemId)
                .build();
    }

    private ImageMetadataDto toImageMetadataDto(ImageMetadata metadata) {
        return ImageMetadataDto.builder()
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .cameraMake(metadata.getCameraMake())
                .cameraModel(metadata.getCameraModel())
                .lens(metadata.getLens())
                .iso(metadata.getIso())
                .aperture(metadata.getAperture())
                .shutterSpeed(metadata.getShutterSpeed())
                .takenAt(metadata.getTakenAt() != null ? metadata.getTakenAt().toString() : null)
                .gpsLatitude(metadata.getGpsLatitude())
                .gpsLongitude(metadata.getGpsLongitude())
                .build();
    }

    private AudioMetadataDto toAudioMetadataDto(AudioMetadata metadata) {
        return AudioMetadataDto.builder()
                .artist(metadata.getArtist())
                .album(metadata.getAlbum())
                .albumArtist(metadata.getAlbumArtist())
                .trackNumber(metadata.getTrackNumber())
                .discNumber(metadata.getDiscNumber())
                .genres(readStringList(metadata.getGenres()))
                .durationSeconds(metadata.getDurationSeconds())
                .bitrate(metadata.getBitrate())
                .sampleRate(metadata.getSampleRate())
                .channels(metadata.getChannels())
                .build();
    }

    private TagDto toTagDto(Tag tag) {
        return TagDto.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .build();
    }

    private CategoryDto toCategoryDto(Category category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .type(category.getType())
                .build();
    }

    private java.util.List<String> readStringList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<java.util.List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> getFilterOptions() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("types", List.of("MOVIE", "TV_SHOW", "IMAGE", "AUDIO"));
        filters.put("tags", tagRepository.findAll().stream()
                .map(t -> Map.of("id", t.getId(), "name", t.getName()))
                .collect(Collectors.toList()));
        filters.put("categories", categoryRepository.findAll().stream()
                .map(c -> Map.of("id", c.getId(), "name", c.getName(), "type", c.getType()))
                .collect(Collectors.toList()));
        return filters;
    }

    @Transactional
    public MediaItemResponse updateMetadata(Integer id, Map<String, Object> metadata) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanEditItem(item);

        if (metadata.containsKey("title")) item.setTitle((String) metadata.get("title"));
        if (metadata.containsKey("originalTitle")) item.setOriginalTitle((String) metadata.get("originalTitle"));
        if (metadata.containsKey("overview")) item.setOverview((String) metadata.get("overview"));
        if (metadata.containsKey("rating")) {
            Object val = metadata.get("rating");
            if (val instanceof Number) item.setRating(BigDecimal.valueOf(((Number) val).doubleValue()));
        }

        if ("TV_SHOW".equals(item.getType())) {
            TvShowMetadata tm = tvShowMetadataRepository.findByMediaItemId(item.getId())
                    .orElse(TvShowMetadata.builder().mediaItem(item).build());
            if (metadata.containsKey("network")) {
                tm.setNetwork((String) metadata.get("network"));
            }
            if (metadata.containsKey("tvStatus")) {
                tm.setStatus((String) metadata.get("tvStatus"));
            }
            if (metadata.containsKey("genres")) {
                Object genres = metadata.get("genres");
                if (genres instanceof List<?> list) {
                    try {
                        tm.setGenres(objectMapper.writeValueAsString(list));
                    } catch (Exception ignored) {
                        // keep existing
                    }
                } else if (genres instanceof String s) {
                    tm.setGenres(s);
                }
            }
            tvShowMetadataRepository.save(tm);
        }

        MediaItem saved = itemRepository.save(item);
        nfoExportService.export(saved);
        mediaPostProcessService.afterMetadataUpdatedAsync(saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void classifyItem(Integer id) {
        MediaItem item = requireVisibleItem(id);
        libraryAccessService.assertCanEditItem(item);
        classificationEngine.executeClassification(item);
    }

    public Map<String, Object> classifyBatch(List<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "itemIds 不能为空");
        }
        if (itemIds.size() > 100) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "单次最多分类 100 条");
        }
        int ok = 0;
        int failed = 0;
        for (Integer id : itemIds) {
            try {
                classifyItem(id);
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("Batch classify failed for item {}: {}", id, e.getMessage());
            }
        }
        return Map.of("requested", itemIds.size(), "succeeded", ok, "failed", failed);
    }

    @Transactional
    public void refreshMetadata(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanEditItem(item);
        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);
        MetadataResult result = metadataPipelineService.executeLocalPipeline(item, primaryFile);
        if (result.getTitle() == null && primaryFile != null) {
            result.mergeFrom(fileNameParser.parse(primaryFile.getFileName()));
        }
        metadataApplyService.applyResult(item, result, primaryFile);
        nfoExportService.export(item);
        mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
    }

    @Transactional
    public void deleteItem(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanDeleteItem(item);
        mediaFileLifecycleService.softDeleteActiveFiles(item);
    }

    @Transactional
    public void deleteSourceFile(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanDeleteItem(item);
        mediaFileLifecycleService.deleteSourceFiles(item);
    }

    @Transactional
    public MediaItemResponse identifyItem(Integer id, IdentifyRequest request) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanEditItem(item);

        String provider = request.getProvider() != null ? request.getProvider().toLowerCase() : "tmdb";
        if (request.getExternalId() == null || request.getExternalId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "externalId is required");
        }

        var library = libraryRepository.findWithDetailsById(item.getLibrary().getId())
                .orElse(item.getLibrary());

        String configJson = extractorConfigJson(library, provider.toUpperCase());
        LibraryPluginConfig pluginConfig = LibraryPluginConfig.builder()
                .library(library)
                .pluginId(provider)
                .kind(PluginKind.SCRAPER.name())
                .enabled(true)
                .priority(100)
                .config(configJson)
                .build();

        MetadataScraper scraper = (MetadataScraper) pluginRegistry.find(PluginKind.SCRAPER, provider)
                .map(PluginRegistry.RegisteredPlugin::delegate)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAMETER, "Unsupported provider: " + provider));

        MetadataResult result = scraper.fetchByExternalId(request.getExternalId(), pluginConfig, item.getType(), library.getLanguage());

        if (result == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "No metadata returned for provider: " + provider);
        }

        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);
        metadataApplyService.applyResult(item, result, primaryFile);
        nfoExportService.export(item);
        if ("tmdb".equals(provider) && "TV_SHOW".equals(item.getType())) {
            String apiKey = tmdbClientService.resolveApiKeyForLibrary(library);
            tvSeasonSyncService.syncFromTmdb(item, apiKey, request.getExternalId(), library.getLanguage());
        }
        mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
        return toResponse(itemRepository.findById(id).orElseThrow());
    }

    private String extractorConfigJson(com.mediamanager.library.entity.MediaLibrary library, String type) {
        return pluginConfigResolver.resolveConfigJson(library, type);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchScraperCandidates(Integer id, String provider, String query) {
        MediaItem item = requireVisibleItem(id);
        var library = libraryRepository.findWithDetailsById(item.getLibrary().getId()).orElse(item.getLibrary());
        String configJson = extractorConfigJson(library, provider.toUpperCase());
        LibraryPluginConfig pluginConfig = LibraryPluginConfig.builder()
                .library(library)
                .pluginId(provider)
                .kind(PluginKind.SCRAPER.name())
                .enabled(true)
                .priority(100)
                .config(configJson)
                .build();

        MetadataScraper scraper = (MetadataScraper) pluginRegistry.find(PluginKind.SCRAPER, provider)
                .map(PluginRegistry.RegisteredPlugin::delegate)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARAMETER, "Unsupported provider: " + provider));

        return scraper.searchCandidates(query, pluginConfig, item.getType(), library.getLanguage());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchTmdbCandidates(Integer id, String query) {
        return searchScraperCandidates(id, "tmdb", query);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchJavBusCandidates(Integer id, String query) {
        return searchScraperCandidates(id, "javbus", query);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchStashDbCandidates(Integer id, String query) {
        return searchScraperCandidates(id, "stashdb", query);
    }

    public ResponseEntity<Resource> getImage(Integer id, String type) throws IOException {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);

        String imagePath = "poster".equals(type) ? item.getPosterPath() : item.getBackdropPath();
        if (imagePath == null || imagePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String trimmed = imagePath.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(trimmed)).build();
        }

        String resolvedPath = storagePathMapper.mapPathIfNeeded(trimmed);
        Path path = Path.of(resolvedPath);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(path);
        String contentType = Files.probeContentType(path);
        if (contentType == null) contentType = "image/jpeg";

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .body(resource);
    }

    public ResponseEntity<Resource> getPreviewImage(Integer id) throws IOException {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);

        String previewPath = thumbnailService.getPreviewWebpPath(id);
        Path path = Path.of(previewPath);
        if (!Files.exists(path)) {
            // Fallback to static poster
            return getImage(id, "poster");
        }

        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header("Content-Type", "image/webp")
                .body(resource);
    }

    private Sort resolveSort(String sortField, String sortOrder) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String field = sortField != null ? sortField : "createdAt";
        return switch (field) {
            case "title" -> Sort.by(direction, "title");
            case "rating" -> Sort.by(direction, "rating");
            case "releaseDate" -> Sort.by(direction, "releaseDate");
            default -> Sort.by(direction, "createdAt");
        };
    }

    private MediaItem requireVisibleItem(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        if (Boolean.TRUE.equals(item.getHidden())) {
            return resolveVisibleReplacement(item)
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        }
        return item;
    }

    private java.util.Optional<MediaItem> resolveVisibleReplacement(MediaItem hidden) {
        if (hidden.getTitle() == null || hidden.getTitle().isBlank()) {
            return java.util.Optional.empty();
        }
        var replacements = itemRepository.findVisibleReplacementsByTitle(
                hidden.getLibrary().getId(), hidden.getTitle());
        return replacements.stream().findFirst();
    }

}
