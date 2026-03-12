package com.mediamanager.classification.service;

import com.mediamanager.classification.spi.ClassifierStrategy;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class ClassificationEngine {

    private final List<ClassifierStrategy> strategies;
    private final MediaItemRepository itemRepository;

    public ClassificationEngine(List<ClassifierStrategy> strategies, MediaItemRepository itemRepository) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(ClassifierStrategy::getPriority))
                .toList();
        this.itemRepository = itemRepository;
    }

    @Transactional
    public void executeClassification(MediaItem item) {
        log.debug("Executing classification engine for item: {}", item.getId());
        for (ClassifierStrategy strategy : strategies) {
            try {
                strategy.classify(item);
            } catch (Exception e) {
                log.error("Classifier {} failed for item {}", strategy.getClass().getSimpleName(), item.getId(), e);
            }
        }
        itemRepository.save(item);
    }
}
