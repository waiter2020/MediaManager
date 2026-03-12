package com.mediamanager.media.repository;

import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MediaItemSpecification {

    public static Specification<MediaItem> filterBy(
            Integer libraryId,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (libraryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("library").get("id"), libraryId));
            }

            if (type != null && !type.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }

            if (keyword != null && !keyword.isEmpty()) {
                String likeKeyword = "%" + keyword.toLowerCase() + "%";
                Predicate titlePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likeKeyword);
                Predicate originalTitlePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("originalTitle")), likeKeyword);
                predicates.add(criteriaBuilder.or(titlePredicate, originalTitlePredicate));
            }

            if (categoryIds != null && !categoryIds.isEmpty()) {
                Join<MediaItem, Category> categoryJoin = root.join("categories");
                predicates.add(categoryJoin.get("id").in(categoryIds));
            }

            if (tagIds != null && !tagIds.isEmpty()) {
                Join<MediaItem, Tag> tagJoin = root.join("tags");
                predicates.add(tagJoin.get("id").in(tagIds));
            }

            // Distict results because of joins
            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
