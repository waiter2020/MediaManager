package com.mediamanager.classification.repository;

import com.mediamanager.classification.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByParentId(Integer parentId);

    List<Category> findByType(String type);

    Optional<Category> findByParentIdAndName(Integer parentId, String name);
}
