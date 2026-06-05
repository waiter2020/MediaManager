package com.mediamanager.search.config;

import com.mediamanager.search.service.FtsIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FtsIndexBootstrap implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final FtsIndexService ftsIndexService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer ftsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_fts", Integer.class);
            Integer itemCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM media_item WHERE hidden = 0", Integer.class);
            if (ftsCount != null && ftsCount == 0 && itemCount != null && itemCount > 0) {
                int indexed = ftsIndexService.rebuildAll();
                log.info("FTS index bootstrap completed: {} items", indexed);
            }
        } catch (Exception e) {
            log.warn("FTS bootstrap skipped: {}", e.getMessage());
        }
    }
}
