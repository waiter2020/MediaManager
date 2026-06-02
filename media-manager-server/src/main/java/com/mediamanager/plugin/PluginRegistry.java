package com.mediamanager.plugin;

import com.mediamanager.ai.spi.AiProvider;
import com.mediamanager.classification.spi.ClassifierStrategy;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataScraper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PluginRegistry {

    private final Map<String, RegisteredPlugin> byKey;

    public PluginRegistry(
            List<MetadataExtractor> extractors,
            List<MetadataScraper> scrapers,
            List<ClassifierStrategy> classifiers,
            List<AiProvider> aiProviders) {
        List<RegisteredPlugin> all = new ArrayList<>();
        for (MetadataExtractor extractor : extractors) {
            all.add(new RegisteredPlugin(
                    extractor.id(),
                    extractor.kind(),
                    extractor.displayName(),
                    extractor));
        }
        for (MetadataScraper scraper : scrapers) {
            all.add(new RegisteredPlugin(
                    scraper.id(),
                    scraper.kind(),
                    scraper.displayName(),
                    scraper));
        }
        for (ClassifierStrategy classifier : classifiers) {
            all.add(new RegisteredPlugin(
                    classifier.id(),
                    classifier.kind(),
                    classifier.displayName(),
                    classifier));
        }
        for (AiProvider provider : aiProviders) {
            if ("noop".equals(provider.providerId())) {
                continue;
            }
            all.add(new RegisteredPlugin(
                    provider.id(),
                    provider.kind(),
                    provider.displayName(),
                    provider));
        }
        this.byKey = all.stream().collect(Collectors.toMap(
                p -> p.kind().name() + ":" + p.id(),
                Function.identity(),
                (a, b) -> a));
        log.info("Plugin registry loaded: {} plugins ({})",
                byKey.size(),
                all.stream().map(p -> p.kind().name() + ":" + p.id()).sorted().toList());
    }

    public List<RegisteredPlugin> listByKind(PluginKind kind) {
        return byKey.values().stream()
                .filter(p -> p.kind() == kind)
                .collect(Collectors.toList());
    }

    public Optional<RegisteredPlugin> find(PluginKind kind, String id) {
        return Optional.ofNullable(byKey.get(kind.name() + ":" + id.toLowerCase()));
    }

    public List<Map<String, Object>> listDescriptors() {
        return byKey.values().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.id(),
                        "kind", p.kind().name(),
                        "displayName", p.displayName()))
                .collect(Collectors.toList());
    }

    public record RegisteredPlugin(String id, PluginKind kind, String displayName, Object delegate) {}
}
