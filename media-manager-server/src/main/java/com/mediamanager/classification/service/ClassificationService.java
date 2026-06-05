package com.mediamanager.classification.service;

import com.mediamanager.classification.dto.CategoryResponse;
import com.mediamanager.classification.dto.TagResponse;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClassificationService {

    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags() {
        return tagRepository.findGlobalUsageCounts().stream()
                .map(this::toTagResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree() {
        List<Category> allCats = categoryRepository.findAll();
        
        List<CategoryResponse> rootCats = allCats.stream()
                .filter(c -> c.getParentId() == null)
                .map(root -> toCategoryTreeResponse(root, allCats))
                .collect(Collectors.toList());
                
        return rootCats;
    }
    
    private CategoryResponse toCategoryTreeResponse(Category cat, List<Category> allCats) {
        List<CategoryResponse> children = allCats.stream()
                .filter(c -> c.getParentId() != null && c.getParentId().equals(cat.getId()))
                .map(c -> toCategoryTreeResponse(c, allCats))
                .collect(Collectors.toList());
                
        return CategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .parentId(cat.getParentId())
                .type(cat.getType())
                .children(children)
                .build();
    }

    private TagResponse toTagResponse(TagRepository.TagUsageProjection row) {
        return TagResponse.builder()
                .id(row.getTagId())
                .name(row.getTagName())
                .color(row.getColor())
                .source(row.getSource())
                .usageCount(row.getUsageCount() != null ? row.getUsageCount() : 0L)
                .createdAt(row.getCreatedAt())
                .build();
    }
}
