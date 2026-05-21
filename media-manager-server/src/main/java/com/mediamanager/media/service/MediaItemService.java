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
import com.mediamanager.metadata.dto.ImageMetadataDto;
import com.mediamanager.metadata.dto.AudioMetadataDto;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.ImageMetadata;
import com.mediamanager.metadata.entity.AudioMetadata;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.ImageMetadataRepository;
import com.mediamanager.metadata.repository.AudioMetadataRepository;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.metadata.service.MetadataPipelineService;
import com.mediamanager.metadata.service.MetadataApplyService;
import com.mediamanager.metadata.service.TmdbClientService;
import com.mediamanager.metadata.dto.IdentifyRequest;
import com.mediamanager.metadata.spi.MetadataResult;
import com.mediamanager.metadata.util.FileNameParser;
import com.mediamanager.search.service.FtsIndexService;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ImageMetadataRepository imageMetadataRepository;
    private final AudioMetadataRepository audioMetadataRepository;
    private final ObjectMapper objectMapper;
    private final LibraryAccessService libraryAccessService;
    private final TmdbClientService tmdbClientService;
    private final MetadataApplyService metadataApplyService;
    private final MediaPostProcessService mediaPostProcessService;
    private final FtsIndexService ftsIndexService;
    private final FileNameParser fileNameParser;

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> getItems(
            Integer libraryId,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            int page,
            int size,
            String sortField,
            String sortOrder) {

        PageRequest pageRequest = PageRequest.of(page - 1, size, resolveSort(sortField, sortOrder));
        
        Set<Integer> libraryIds = libraryAccessService.resolveLibraryFilter(libraryId);
        Specification<MediaItem> spec = MediaItemSpecification.filterBy(libraryIds, type, keyword, categoryIds, tagIds);
        
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
        ImageMetadataDto imageMetadataDto = null;
        AudioMetadataDto audioMetadataDto = null;

        if ("MOVIE".equals(item.getType()) || "TV_SHOW".equals(item.getType())) {
            movieMetadataDto = movieMetadataRepository.findByMediaItemId(item.getId())
                    .map(this::toMovieMetadataDto)
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
                .imageMetadata(imageMetadataDto)
                .audioMetadata(audioMetadataDto)
                .build();
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
        libraryAccessService.assertCanViewItem(item);

        if (metadata.containsKey("title")) item.setTitle((String) metadata.get("title"));
        if (metadata.containsKey("originalTitle")) item.setOriginalTitle((String) metadata.get("originalTitle"));
        if (metadata.containsKey("overview")) item.setOverview((String) metadata.get("overview"));
        if (metadata.containsKey("rating")) {
            Object val = metadata.get("rating");
            if (val instanceof Number) item.setRating(BigDecimal.valueOf(((Number) val).doubleValue()));
        }

        return toResponse(itemRepository.save(item));
    }

    @Transactional
    public void refreshMetadata(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);
        MetadataResult result = metadataPipelineService.executeLocalPipeline(item, primaryFile);
        if (result.getTitle() == null && primaryFile != null) {
            result.mergeFrom(fileNameParser.parse(primaryFile.getFileName()));
        }
        metadataApplyService.applyResult(item, result, primaryFile);
        mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
    }

    @Transactional
    public void deleteItem(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        for (MediaFile file : fileRepository.findByMediaItemId(item.getId())) {
            if (!Boolean.TRUE.equals(file.getDeleted())) {
                file.setDeleted(true);
                file.setDeletedAt(Instant.now());
                fileRepository.save(file);
            }
        }
        syncItemVisibility(item);
    }

    @Transactional
    public void deleteSourceFile(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        for (MediaFile file : files) {
            try {
                Files.deleteIfExists(Path.of(file.getFilePath()));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.FILE_SYSTEM_ERROR, "Failed to delete: " + file.getFilePath());
            }
            file.setDeleted(true);
            file.setDeletedAt(Instant.now());
            fileRepository.save(file);
        }
        syncItemVisibility(item);
    }

    @Transactional
    public MediaItemResponse identifyItem(Integer id, IdentifyRequest request) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);

        String provider = request.getProvider() != null ? request.getProvider() : "tmdb";
        if (!"tmdb".equalsIgnoreCase(provider)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Unsupported provider: " + provider);
        }
        if (request.getExternalId() == null || request.getExternalId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "externalId is required");
        }

        String apiKey = tmdbClientService.resolveApiKeyForLibrary(item.getLibrary());
        MetadataResult result = tmdbClientService.fetchByExternalId(
                apiKey,
                item.getType(),
                item.getLibrary().getLanguage(),
                request.getExternalId());

        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);
        metadataApplyService.applyResult(item, result, primaryFile);
        mediaPostProcessService.afterMetadataUpdatedAsync(item.getId());
        return toResponse(itemRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchTmdbCandidates(Integer id, String query) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        String apiKey = tmdbClientService.resolveApiKeyForLibrary(item.getLibrary());
        return tmdbClientService.search(apiKey, item.getType(), item.getLibrary().getLanguage(), query);
    }

    public ResponseEntity<Resource> getImage(Integer id, String type) throws IOException {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);

        String imagePath = "poster".equals(type) ? item.getPosterPath() : item.getBackdropPath();
        if (imagePath == null || imagePath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path path = Path.of(imagePath);
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
            throw new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND);
        }
        return item;
    }

    private void syncItemVisibility(MediaItem item) {
        boolean hasActive = !fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).isEmpty();
        item.setHidden(!hasActive);
        itemRepository.save(item);
        if (Boolean.TRUE.equals(item.getHidden())) {
            ftsIndexService.removeItem(item.getId());
        } else {
            ftsIndexService.indexItem(item);
        }
    }
}
