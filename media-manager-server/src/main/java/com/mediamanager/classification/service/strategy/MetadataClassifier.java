package com.mediamanager.classification.service.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.spi.ClassifierStrategy;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import com.mediamanager.metadata.repository.TvShowMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataClassifier implements ClassifierStrategy {

    private final MovieMetadataRepository movieRepo;
    private final TvShowMetadataRepository tvRepo;
    private final CategoryRepository categoryRepo;
    private final ObjectMapper objectMapper;

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public void classify(MediaItem item) {
        // 1. Classify Release Year
        classifyYear(item);

        // 2. Classify Genres from Metadata
        if ("MOVIE".equals(item.getType())) {
            movieRepo.findByMediaItemId(item.getId()).ifPresent(meta -> classifyGenres(item, meta.getGenres()));
        } else if ("TV_SHOW".equals(item.getType())) {
            tvRepo.findByMediaItemId(item.getId()).ifPresent(meta -> classifyGenres(item, meta.getGenres()));
        }
    }

    private void classifyYear(MediaItem item) {
        LocalDate releaseDate = item.getReleaseDate();
        if (releaseDate == null) return;

        String yearStr = String.valueOf(releaseDate.getYear());
        
        // Find Year root category
        List<Category> yearRoots = categoryRepo.findByType("YEAR");
        if (yearRoots.isEmpty()) return;
        Category root = yearRoots.get(0);

        // Find or create exact year category
        Category yearCat = categoryRepo.findByParentIdAndName(root.getId(), yearStr)
                .orElseGet(() -> categoryRepo.save(Category.builder()
                        .name(yearStr)
                        .parentId(root.getId())
                        .type("YEAR")
                        .build()));

        item.getCategories().add(yearCat);
    }

    private void classifyGenres(MediaItem item, String genresJson) {
        if (genresJson == null || genresJson.isEmpty()) return;
        
        try {
            JsonNode genresNode = objectMapper.readTree(genresJson);
            if (!genresNode.isArray()) return;

            // Find Genre root category
            List<Category> genreRoots = categoryRepo.findByType("GENRE");
            if (genreRoots.isEmpty()) return;
            Category root = genreRoots.get(0);

            for (JsonNode n : genresNode) {
                String genreName = n.asText();
                Category genreCat = categoryRepo.findByParentIdAndName(root.getId(), genreName)
                        .orElseGet(() -> categoryRepo.save(Category.builder()
                                .name(genreName)
                                .parentId(root.getId())
                                .type("GENRE")
                                .build()));

                item.getCategories().add(genreCat);
            }
        } catch (Exception e) {
            log.error("Failed to parse genres JSON for item {}", item.getId(), e);
        }
    }
}
