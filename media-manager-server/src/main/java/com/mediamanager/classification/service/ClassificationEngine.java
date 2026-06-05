package com.mediamanager.classification.service;

import com.mediamanager.classification.spi.ClassifierStrategy;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ClassificationEngine {

    private final List<ClassifierStrategy> strategies;
    private final MediaItemRepository itemRepository;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate noTransaction;

    public ClassificationEngine(
            List<ClassifierStrategy> strategies,
            MediaItemRepository itemRepository,
            PlatformTransactionManager transactionManager) {
        this.strategies = strategies.stream()
                .sorted(Comparator.comparingInt(ClassifierStrategy::getPriority))
                .toList();
        this.itemRepository = itemRepository;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.noTransaction = new TransactionTemplate(transactionManager);
        this.noTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    public MediaItem executeClassification(MediaItem item) {
        if (item == null || item.getId() == null) {
            return item;
        }

        Integer itemId = item.getId();
        log.debug("Executing classification engine for item: {}", item.getId());
        MediaItem classifiedItem = runTransactionalStrategies(itemId);

        if (classifiedItem == null) {
            return item;
        }

        noTransaction.executeWithoutResult(status -> runStrategies(classifiedItem, false));
        return classifiedItem;
    }

    public BatchClassificationResult executeBatchClassification(List<MediaItem> items) {
        if (items == null || items.isEmpty()) {
            return new BatchClassificationResult(0, 0, 0, List.of());
        }

        Set<Integer> itemIds = new LinkedHashSet<>();
        for (MediaItem item : items) {
            if (item != null && item.getId() != null) {
                itemIds.add(item.getId());
            }
        }

        List<MediaItem> classifiedItems = new ArrayList<>();
        int failed = 0;
        for (Integer itemId : itemIds) {
            try {
                MediaItem classified = runTransactionalStrategies(itemId);
                if (classified == null) {
                    failed++;
                    continue;
                }
                classifiedItems.add(classified);
            } catch (Exception e) {
                failed++;
                log.error("Transactional classification failed for item {}", itemId, e);
            }
        }

        if (!classifiedItems.isEmpty()) {
            noTransaction.executeWithoutResult(status -> runBatchStrategies(classifiedItems, false));
        }
        return new BatchClassificationResult(itemIds.size(), classifiedItems.size(), failed, List.copyOf(classifiedItems));
    }

    private MediaItem runTransactionalStrategies(Integer itemId) {
        return writeTransaction.execute(status -> {
            MediaItem managed = itemRepository.findByIdWithClassificationGraph(itemId)
                    .orElse(null);
            if (managed == null) {
                log.warn("Skipping classification because media item {} no longer exists", itemId);
                return null;
            }
            runStrategies(managed, true);
            return itemRepository.save(managed);
        });
    }

    private void runStrategies(MediaItem item, boolean transactional) {
        for (ClassifierStrategy strategy : strategies) {
            if (strategy.runsInTransaction() != transactional) {
                continue;
            }
            try {
                strategy.classify(item);
            } catch (Exception e) {
                log.error("Classifier {} failed for item {}", strategy.getClass().getSimpleName(), item.getId(), e);
            }
        }
    }

    private void runBatchStrategies(List<MediaItem> items, boolean transactional) {
        for (ClassifierStrategy strategy : strategies) {
            if (strategy.runsInTransaction() != transactional) {
                continue;
            }
            try {
                strategy.classifyBatch(items);
            } catch (Exception e) {
                log.error("Batch classifier {} failed for {} items",
                        strategy.getClass().getSimpleName(), items.size(), e);
            }
        }
    }

    public record BatchClassificationResult(
            int requested,
            int succeeded,
            int failed,
            List<MediaItem> classifiedItems) {
    }
}
