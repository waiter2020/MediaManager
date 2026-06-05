package com.mediamanager.common.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class StoragePathMapper {

    @Value("${mediamanager.storage.path-mappings:}")
    private String pathMappings;

    @Value("${mediamanager.storage.path-map-from:}")
    private String pathMapFrom;

    @Value("${mediamanager.storage.path-map-to:}")
    private String pathMapTo;

    @PostConstruct
    void logConfiguredMappings() {
        log.info("Storage path map: mappings={}, from={}, to={}",
                maskPathMappings(), maskPathMapFrom(), maskPathMapTo());
    }

    public String mapPathIfNeeded(String originalPath) {
        if (originalPath == null || originalPath.isBlank()) {
            return originalPath;
        }
        String mapped = mapFromMappings(originalPath);
        if (mapped != null) {
            return mapped;
        }

        if (pathMapFrom != null && !pathMapFrom.isBlank() && pathMapTo != null && !pathMapTo.isBlank()) {
            mapped = mapPath(originalPath, pathMapFrom, pathMapTo);
            if (mapped != null) {
                return mapped;
            }
        }

        return originalPath;
    }

    public String maskPathMappings() {
        return maskMappings(pathMappings);
    }

    public String maskPathMapFrom() {
        return maskPath(pathMapFrom);
    }

    public String maskPathMapTo() {
        return maskPath(pathMapTo);
    }

    private String mapFromMappings(String originalPath) {
        if (pathMappings == null || pathMappings.isBlank()) {
            return null;
        }
        for (String entry : pathMappings.split(";")) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                log.warn("Ignoring invalid storage path mapping entry: {}", entry);
                continue;
            }
            String from = entry.substring(0, separator).trim();
            String to = entry.substring(separator + 1).trim();
            String mapped = mapPath(originalPath, from, to);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    private String mapPath(String originalPath, String pathFrom, String pathTo) {
        if (pathFrom == null || pathFrom.isBlank() || pathTo == null || pathTo.isBlank()) {
            return null;
        }
        String p = originalPath.replace('\\', '/');
        String from = pathFrom.replace('\\', '/');
        String to = pathTo.replace('\\', '/');

        if (isPathPrefix(p, from)) {
            String suffix = p.substring(stripTrailingSlash(from).length());
            if (!suffix.startsWith("/")) suffix = "/" + suffix;
            String mapped = to.endsWith("/") ? to.substring(0, to.length() - 1) : to;
            return mapped + suffix;
        }

        return null;
    }

    private boolean isPathPrefix(String path, String prefix) {
        String p = stripTrailingSlash(path);
        String from = stripTrailingSlash(prefix);
        if (from.isBlank()) {
            return false;
        }
        if (!p.regionMatches(true, 0, from, 0, from.length())) {
            return false;
        }
        return p.length() == from.length() || p.charAt(from.length()) == '/';
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String maskPath(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash < 0) {
            return normalized;
        }
        String leaf = normalized.substring(slash + 1);
        String root = normalized.substring(0, Math.min(normalized.length(), slash + 1));
        return root.toLowerCase(Locale.ROOT).contains("users/") ? "*/" + leaf : normalized;
    }

    private String maskMappings(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        return String.join(";",
                List.of(value.split(";")).stream()
                        .map(entry -> {
                            int separator = entry.indexOf('=');
                            if (separator <= 0) {
                                return entry;
                            }
                            return maskPath(entry.substring(0, separator)) + "=" + maskPath(entry.substring(separator + 1));
                        })
                        .toList());
    }
}
