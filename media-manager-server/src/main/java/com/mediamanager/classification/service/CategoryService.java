package com.mediamanager.classification.service;

import com.mediamanager.classification.dto.*;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategoryTree() {
        List<Category> roots = categoryRepository.findByParentId(null);
        return roots.stream().map(this::toTreeResponse).collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setType(request.getType());

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Integer id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        if (request.getName() != null) {
            category.setName(request.getName());
        }
        if (request.getParentId() != null) {
            category.setParentId(request.getParentId());
        }
        if (request.getType() != null) {
            category.setType(request.getType());
        }

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        categoryRepository.delete(category);
    }

    private CategoryResponse toTreeResponse(Category category) {
        CategoryResponse resp = toResponse(category);
        List<Category> children = categoryRepository.findByParentId(category.getId());
        if (!children.isEmpty()) {
            resp.setChildren(children.stream().map(this::toTreeResponse).collect(Collectors.toList()));
        }
        return resp;
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse resp = new CategoryResponse();
        resp.setId(category.getId());
        resp.setName(category.getName());
        resp.setParentId(category.getParentId());
        resp.setType(category.getType());
        return resp;
    }
}
