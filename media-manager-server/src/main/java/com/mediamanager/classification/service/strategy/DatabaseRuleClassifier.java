package com.mediamanager.classification.service.strategy;

import com.mediamanager.classification.entity.ClassificationRule;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.repository.ClassificationRuleRepository;
import com.mediamanager.classification.service.ClassificationRuleMatcher;
import com.mediamanager.classification.service.TagCanonicalizationService;
import com.mediamanager.classification.spi.ClassifierStrategy;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRuleClassifier implements ClassifierStrategy {

    private final ClassificationRuleRepository ruleRepository;
    private final MediaFileRepository fileRepository;
    private final CategoryRepository categoryRepository;
    private final TagCanonicalizationService tagCanonicalizationService;

    @Override
    public int getPriority() {
        return 15;
    }

    @Override
    public void classify(MediaItem item) {
        List<ClassificationRule> rules = ruleRepository.findByEnabledTrueOrderByPriorityAsc();
        if (rules.isEmpty()) {
            return;
        }
        MediaFile primary = fileRepository.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .findFirst()
                .orElse(null);

        for (ClassificationRule rule : rules) {
            try {
                if (!matchesRule(item, primary, rule)) {
                    continue;
                }
                applyTarget(item, rule);
            } catch (Exception e) {
                log.warn("Rule {} failed for item {}: {}", rule.getId(), item.getId(), e.getMessage());
            }
        }
    }

    private boolean matchesRule(MediaItem item, MediaFile file, ClassificationRule rule) {
        String type = rule.getRuleType() != null ? rule.getRuleType().toUpperCase(Locale.ROOT) : "";
        return switch (type) {
            case "PATH" -> ClassificationRuleMatcher.matchesPath(item, file, rule.getExpression());
            case "METADATA" -> ClassificationRuleMatcher.matchesMetadata(item, rule.getExpression());
            case "FILE" -> ClassificationRuleMatcher.matchesFile(file, rule.getExpression());
            default -> false;
        };
    }

    private void applyTarget(MediaItem item, ClassificationRule rule) {
        String targetType = rule.getTargetType() != null ? rule.getTargetType().toUpperCase(Locale.ROOT) : "";
        String value = rule.getTargetValue();
        if (value == null || value.isBlank()) {
            return;
        }
        if ("TAG".equals(targetType)) {
            tagCanonicalizationService.findOrCreateTag(value, "RULE", "#1677ff")
                    .ifPresent(tag -> tagCanonicalizationService.addCanonicalTag(item, tag));
        } else if ("CATEGORY".equals(targetType)) {
            categoryRepository.findAll().stream()
                    .filter(c -> value.equals(c.getName()))
                    .findFirst()
                    .ifPresent(cat -> {
                        if (item.getCategories().stream().noneMatch(c -> c.getId().equals(cat.getId()))) {
                            item.getCategories().add(cat);
                        }
                    });
        }
    }
}
