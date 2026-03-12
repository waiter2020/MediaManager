package com.mediamanager.media.service;

import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.response.PageResult;
import com.mediamanager.media.dto.MediaItemResponse;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaItemSpecification;
import com.mediamanager.metadata.service.MetadataPipelineService;
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

    @Transactional(readOnly = true)
    public PageResult<MediaItemResponse> getItems(
            Integer libraryId, 
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            int page, 
            int size) {
            
        PageRequest pageRequest = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Specification<MediaItem> spec = MediaItemSpecification.filterBy(libraryId, type, keyword, categoryIds, tagIds);
        
        Page<MediaItem> itemPage = itemRepository.findAll(spec, pageRequest);
        
        List<MediaItemResponse> responses = itemPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResult.of(responses, itemPage.getTotalElements(), page, size);
    }
    
    @Transactional(readOnly = true)
    public MediaItemResponse getItem(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        return toResponse(item);
    }

    private MediaItemResponse toResponse(MediaItem item) {
        Float rating = item.getRating() != null ? item.getRating().floatValue() : null;

        List<Integer> fileIds = fileRepository.findByMediaItemId(item.getId()).stream()
                .map(MediaFile::getId)
                .collect(Collectors.toList());

        return MediaItemResponse.builder()
                .id(item.getId())
                .libraryId(item.getLibrary().getId())
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
        List<MediaFile> files = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId());
        MediaFile primaryFile = files.isEmpty() ? null : files.get(0);
        metadataPipelineService.executePipeline(item, primaryFile);
        // If metadata pipeline still did not provide a poster, try to generate a thumbnail
        if (item.getPosterPath() == null
                && ("MOVIE".equals(item.getType()) || "TV_SHOW".equals(item.getType()))
                && primaryFile != null) {
            String thumbnailPath = thumbnailService.generateThumbnail(item.getId(), primaryFile.getFilePath());
            if (thumbnailPath != null) {
                item.setPosterPath(thumbnailPath);
                itemRepository.save(item);
            }
        }
    }

    @Transactional
    public void deleteItem(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        itemRepository.delete(item);
    }

    @Transactional
    public void deleteSourceFile(Integer id) {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
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
    }

    public ResponseEntity<Resource> getImage(Integer id, String type) throws IOException {
        MediaItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));

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
}
