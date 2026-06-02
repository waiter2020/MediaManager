package com.mediamanager.classification.service;

import com.mediamanager.classification.dto.*;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.classification.repository.TagRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;
    private final MediaItemRepository mediaItemRepository;
    private final LibraryAccessService libraryAccessService;

    public List<TagResponse> getAllTags() {
        return tagRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TagResponse createTag(TagCreateRequest request) {
        if (tagRepository.findByName(request.getName()).isPresent()) {
            throw new BusinessException(ErrorCode.TAG_NAME_EXISTS);
        }

        Tag tag = new Tag();
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        tag.setSource("MANUAL");

        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public TagResponse updateTag(Integer id, TagUpdateRequest request) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));

        if (request.getName() != null) {
            tagRepository.findByName(request.getName()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException(ErrorCode.TAG_NAME_EXISTS);
                }
            });
            tag.setName(request.getName());
        }
        if (request.getColor() != null) {
            tag.setColor(request.getColor());
        }

        return toResponse(tagRepository.save(tag));
    }

    @Transactional
    public void deleteTag(Integer id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        tagRepository.delete(tag);
    }

    @Transactional
    public void addTagToItem(Integer mediaItemId, Integer tagId) {
        MediaItem item = mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanEditItem(item);
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
        item.getTags().add(tag);
        mediaItemRepository.save(item);
    }

    @Transactional
    public void removeTagFromItem(Integer mediaItemId, Integer tagId) {
        MediaItem item = mediaItemRepository.findById(mediaItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanEditItem(item);
        item.getTags().removeIf(t -> t.getId().equals(tagId));
        mediaItemRepository.save(item);
    }

    @Transactional
    public void batchAddTags(BatchTagRequest request) {
        List<Tag> tags = tagRepository.findAllById(request.getTagIds());
        List<MediaItem> items = mediaItemRepository.findAllById(request.getMediaItemIds());
        for (MediaItem item : items) {
            libraryAccessService.assertCanEditItem(item);
            item.getTags().addAll(tags);
        }
        mediaItemRepository.saveAll(items);
    }

    private TagResponse toResponse(Tag tag) {
        TagResponse response = new TagResponse();
        response.setId(tag.getId());
        response.setName(tag.getName());
        response.setColor(tag.getColor());
        response.setSource(tag.getSource());
        response.setCreatedAt(tag.getCreatedAt());
        return response;
    }
}
