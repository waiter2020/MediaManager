package com.mediamanager.media.repository;

import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.entity.AudioMetadata;
import com.mediamanager.metadata.entity.ImageMetadata;
import com.mediamanager.metadata.entity.MovieMetadata;
import com.mediamanager.metadata.entity.TvShowMetadata;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MediaItemSpecification {

    public static Specification<MediaItem> filterBy(
            Set<Integer> libraryIds,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds) {
        return filterBy(libraryIds, type, keyword, categoryIds, tagIds, null, null, null, null, null, true);
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
        return filterBy(libraryIds, type, keyword, categoryIds, tagIds, minYear, maxYear, minRating, null, null, true);
    }

    public static Specification<MediaItem> filterBy(
            Set<Integer> libraryIds,
            String type,
            String keyword,
            Set<Integer> categoryIds,
            Set<Integer> tagIds,
            boolean visibleOnly) {
        return filterBy(libraryIds, type, keyword, categoryIds, tagIds, null, null, null, null, null, visibleOnly);
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
            String metadataField,
            String metadataValue) {
        return filterBy(
                libraryIds,
                type,
                keyword,
                categoryIds,
                tagIds,
                minYear,
                maxYear,
                minRating,
                metadataField,
                metadataValue,
                true);
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
            String metadataField,
            String metadataValue,
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
            Predicate metadataPredicate = metadataPredicate(
                    root,
                    query,
                    criteriaBuilder,
                    metadataField,
                    metadataValue);
            if (metadataPredicate != null) {
                predicates.add(metadataPredicate);
            }

            // Distict results because of joins
            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate metadataPredicate(
            Root<MediaItem> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String metadataField,
            String metadataValue) {
        String field = trimToNull(metadataField);
        String value = trimToNull(metadataValue);
        if (field == null || value == null) {
            return null;
        }
        String normalizedField = field.toLowerCase(Locale.ROOT);
        String pattern = "%" + escapeLike(value.toLowerCase(Locale.ROOT)) + "%";
        return switch (normalizedField) {
            case "genre", "genres" -> criteriaBuilder.or(
                    existsMovieText(root, query, criteriaBuilder, "genres", pattern),
                    existsTvText(root, query, criteriaBuilder, "genres", pattern),
                    existsAudioText(root, query, criteriaBuilder, "genres", pattern));
            case "studio", "studios", "publisher" ->
                    existsMovieText(root, query, criteriaBuilder, "studios", pattern);
            case "network" -> existsTvText(root, query, criteriaBuilder, "network", pattern);
            case "actor", "cast", "performer" -> criteriaBuilder.or(
                    existsMovieText(root, query, criteriaBuilder, "castInfo", pattern),
                    existsTvText(root, query, criteriaBuilder, "castInfo", pattern));
            case "artist" -> criteriaBuilder.or(
                    existsAudioText(root, query, criteriaBuilder, "artist", pattern),
                    existsAudioText(root, query, criteriaBuilder, "albumArtist", pattern));
            case "album" -> existsAudioText(root, query, criteriaBuilder, "album", pattern);
            case "camera" -> criteriaBuilder.or(
                    existsImageText(root, query, criteriaBuilder, "cameraMake", pattern),
                    existsImageText(root, query, criteriaBuilder, "cameraModel", pattern));
            default -> null;
        };
    }

    private static Predicate existsMovieText(
            Root<MediaItem> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String attribute,
            String pattern) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<MovieMetadata> metadata = subquery.from(MovieMetadata.class);
        subquery.select(metadata.get("id"));
        subquery.where(
                criteriaBuilder.equal(metadata.get("mediaItem").get("id"), root.get("id")),
                criteriaBuilder.like(criteriaBuilder.lower(metadata.get(attribute)), pattern, '\\'));
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate existsTvText(
            Root<MediaItem> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String attribute,
            String pattern) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<TvShowMetadata> metadata = subquery.from(TvShowMetadata.class);
        subquery.select(metadata.get("id"));
        subquery.where(
                criteriaBuilder.equal(metadata.get("mediaItem").get("id"), root.get("id")),
                criteriaBuilder.like(criteriaBuilder.lower(metadata.get(attribute)), pattern, '\\'));
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate existsAudioText(
            Root<MediaItem> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String attribute,
            String pattern) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<AudioMetadata> metadata = subquery.from(AudioMetadata.class);
        subquery.select(metadata.get("id"));
        subquery.where(
                criteriaBuilder.equal(metadata.get("mediaItem").get("id"), root.get("id")),
                criteriaBuilder.like(criteriaBuilder.lower(metadata.get(attribute)), pattern, '\\'));
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate existsImageText(
            Root<MediaItem> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String attribute,
            String pattern) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<ImageMetadata> metadata = subquery.from(ImageMetadata.class);
        subquery.select(metadata.get("id"));
        subquery.where(
                criteriaBuilder.equal(metadata.get("mediaItem").get("id"), root.get("id")),
                criteriaBuilder.like(criteriaBuilder.lower(metadata.get(attribute)), pattern, '\\'));
        return criteriaBuilder.exists(subquery);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
