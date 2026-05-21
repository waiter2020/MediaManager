package com.mediamanager.search.service;

import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FtsIndexService {

    private final JdbcTemplate jdbcTemplate;
    private final MediaItemRepository itemRepository;

    @Transactional
    public void indexItem(MediaItem item) {
        if (item == null || item.getId() == null || Boolean.TRUE.equals(item.getHidden())) {
            return;
        }
        removeItem(item.getId());
        jdbcTemplate.update(
                "INSERT INTO media_fts(item_id, title, original_title, overview) VALUES (?, ?, ?, ?)",
                item.getId(),
                safe(item.getTitle()),
                safe(item.getOriginalTitle()),
                safe(item.getOverview()));
    }

    @Transactional
    public void removeItem(Integer itemId) {
        if (itemId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM media_fts WHERE item_id = ?", itemId);
    }

    @Transactional(readOnly = true)
    public List<Integer> searchItemIds(String query, Set<Integer> libraryIds, int limit) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String ftsQuery = toFtsQuery(query);
        if (ftsQuery.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Integer> rawIds = jdbcTemplate.queryForList(
                    "SELECT item_id FROM media_fts WHERE media_fts MATCH ? ORDER BY rank LIMIT ?",
                    Integer.class,
                    ftsQuery,
                    Math.max(limit * 5, 50));
            if (rawIds.isEmpty()) {
                return Collections.emptyList();
            }
            return itemRepository.findAllById(rawIds).stream()
                    .filter(i -> !Boolean.TRUE.equals(i.getHidden()))
                    .filter(i -> libraryIds == null || libraryIds.contains(i.getLibrary().getId()))
                    .sorted(Comparator.comparingInt(i -> rawIds.indexOf(i.getId())))
                    .limit(limit)
                    .map(MediaItem::getId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("FTS search failed, caller may fallback: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Transactional
    public int rebuildAll() {
        jdbcTemplate.update("DELETE FROM media_fts");
        List<MediaItem> items = itemRepository.findAll().stream()
                .filter(i -> !Boolean.TRUE.equals(i.getHidden()))
                .toList();
        for (MediaItem item : items) {
            indexItem(item);
        }
        return items.size();
    }

    public static String toFtsQuery(String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] tokens = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            String escaped = token.replace("\"", "\"\"");
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('"').append(escaped).append('"').append('*');
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
