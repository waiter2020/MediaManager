package com.mediamanager.metadata.util;

import com.mediamanager.metadata.spi.MetadataResult;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileNameParser {

    /**
     * Parses convention movie names: Inception (2010).mp4
     */
    private static final Pattern TITLE_YEAR_PATTERN = Pattern.compile("^(.*?)[\\s\\(]+(19\\d{2}|20\\d{2})[\\p{Punct}\\s]*");
    
    /**
     * Parses TV episodes: Breaking Bad S01E03.mkv
     */
    private static final Pattern TV_EPISODE_PATTERN = Pattern.compile("^(.*?)[\\s\\.]+[sS](\\d{1,2})[eE](\\d{1,2})");

    public MetadataResult parse(String fileName) {
        MetadataResult result = MetadataResult.builder().build();

        // Remove extension
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;

        // Try TV Episode
        Matcher tvMatcher = TV_EPISODE_PATTERN.matcher(baseName);
        if (tvMatcher.find()) {
            result.setOriginalTitle(cleanTitle(tvMatcher.group(1)));
            result.setTitle(result.getOriginalTitle());
            result.setSeasonNumber(Integer.parseInt(tvMatcher.group(2)));
            result.setEpisodeNumber(Integer.parseInt(tvMatcher.group(3)));
            return result;
        }

        // Try Movie (Title + Year)
        Matcher movieMatcher = TITLE_YEAR_PATTERN.matcher(baseName);
        if (movieMatcher.find()) {
            result.setOriginalTitle(cleanTitle(movieMatcher.group(1)));
            result.setTitle(result.getOriginalTitle());
            try {
                result.setReleaseDate(java.time.LocalDate.parse(movieMatcher.group(2) + "-01-01"));
            } catch (Exception ignored) {}
            return result;
        }

        // Fallback: Just use cleaned filename as title
        result.setTitle(cleanTitle(baseName));
        result.setOriginalTitle(result.getTitle());
        return result;
    }

    private String cleanTitle(String raw) {
        return raw.replace('.', ' ')
                  .replace('_', ' ')
                  .trim();
    }
}
