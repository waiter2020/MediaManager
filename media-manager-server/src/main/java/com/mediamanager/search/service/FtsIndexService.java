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
                "INSERT INTO media_fts(item_id, title, original_title, overview, file_names, tags, categories) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                item.getId(),
                safe(item.getTitle()),
                safe(item.getOriginalTitle()),
                safe(item.getOverview()),
                joinColumn("SELECT file_name FROM media_file WHERE media_item_id = ? AND deleted = FALSE", item.getId()),
                joinColumn("""
                        SELECT t.name
                        FROM tag t
                        JOIN media_item_tag mit ON mit.tag_id = t.id
                        WHERE mit.media_item_id = ?
                        """, item.getId()),
                joinColumn("""
                        SELECT c.name
                        FROM category c
                        JOIN media_item_category mic ON mic.category_id = c.id
                        WHERE mic.media_item_id = ?
                        """, item.getId()));
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
                    "SELECT item_id FROM media_fts "
                            + "WHERE search_vector @@ to_tsquery('simple', ?) "
                            + "ORDER BY ts_rank(search_vector, to_tsquery('simple', ?)) DESC "
                            + "LIMIT ?",
                    Integer.class,
                    ftsQuery,
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

    @Transactional(readOnly = true)
    public FtsPage searchPage(String query, Set<Integer> libraryIds, int page, int size) {
        if (query == null || query.isBlank() || libraryIds == null || libraryIds.isEmpty()) {
            return new FtsPage(Collections.emptyList(), 0);
        }
        String ftsQuery = toFtsQuery(query);
        if (ftsQuery.isBlank()) {
            return new FtsPage(Collections.emptyList(), 0);
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        String inClause = libraryIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(ftsQuery);
        args.addAll(libraryIds);

        try {
            String where = " FROM media_fts JOIN media_item m ON m.id = media_fts.item_id "
                    + "WHERE media_fts.search_vector @@ to_tsquery('simple', ?) "
                    + "AND (m.hidden = FALSE OR m.hidden IS NULL) "
                    + "AND m.library_id IN (" + inClause + ")";

            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());

            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(ftsQuery);
            pageArgs.add(safeSize);
            pageArgs.add(offset);
            List<Integer> ids = jdbcTemplate.queryForList(
                    "SELECT media_fts.item_id" + where
                            + " ORDER BY ts_rank(media_fts.search_vector, to_tsquery('simple', ?)) DESC LIMIT ? OFFSET ?",
                    Integer.class,
                    pageArgs.toArray());
            return new FtsPage(ids, total != null ? total : 0);
        } catch (Exception e) {
            log.debug("FTS page search failed, caller may fallback: {}", e.getMessage());
            return new FtsPage(Collections.emptyList(), 0);
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
        if (query == null) {
            return "";
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String normalized = trimmed.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_\\s]+", " ");
        String[] tokens = normalized.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" & ");
            }
            sb.append(token).append(":*");
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private String joinColumn(String sql, Integer itemId) {
        try {
            return jdbcTemplate.queryForList(sql, String.class, itemId).stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" "));
        } catch (Exception e) {
            log.debug("Failed to collect FTS field for item {}: {}", itemId, e.getMessage());
            return "";
        }
    }

    public record FtsPage(List<Integer> itemIds, long total) {}
}
