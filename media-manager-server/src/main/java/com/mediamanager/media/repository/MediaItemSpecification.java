package com.mediamanager.media.repository;

import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.media.entity.MediaItem;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MediaItemSpecification {

    public static Specification<MediaItem> filterBy(
            Set<Integer> libraryIds,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds) {
        return filterBy(libraryIds, type, keyword, categoryIds, tagIds, null, null, null, true);
    }

    public static Specification<MediaItem> filterBy(
            Set<Integer> libraryIds,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            Integer minYear,
            Integer maxYear,
            Double minRating) {
        return filterBy(libraryIds, type, keyword, categoryIds, tagIds, minYear, maxYear, minRating, true);
    }

    public static Specification<MediaItem> filterBy(
            Set<Integer> libraryIds,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            boolean visibleOnly) {
        return filterBy(libraryIds, type, keyword, categoryIds, tagIds, null, null, null, visibleOnly);
    }

    public static Specification<MediaItem> filterBy(
            Set<Integer> libraryIds,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            Integer minYear,
            Integer maxYear,
            Double minRating,
            boolean visibleOnly) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (visibleOnly) {
                predicates.add(criteriaBuilder.equal(root.get("hidden"), false));
            }

            if (libraryIds != null) {
                if (libraryIds.isEmpty()) {
                    predicates.add(criteriaBuilder.disjunction()); // always false
                } else if (libraryIds.size() == 1) {
                    predicates.add(criteriaBuilder.equal(root.get("library").get("id"), libraryIds.iterator().next()));
                } else {
                    predicates.add(root.get("library").get("id").in(libraryIds));
                }
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

            if (minYear != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("releaseDate"), LocalDate.of(minYear, 1, 1)));
            }
            if (maxYear != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("releaseDate"), LocalDate.of(maxYear, 12, 31)));
            }
            if (minRating != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("rating"), BigDecimal.valueOf(minRating)));
            }

            // Distict results because of joins
            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
