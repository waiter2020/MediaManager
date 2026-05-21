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

    /**
     * Matches Japanese AV codes like: ABP-123, SSIS-456, MIDV-001, FC2-PPV-1234567
     * Also handles formats without dash: ABP123, SSIS456
     * The pattern is: 2-10 uppercase letters, optional dash, 2-7 digits
     * Special case for FC2-PPV pattern.
     */
    private static final Pattern JAV_CODE_PATTERN = Pattern.compile(
            "(?i)\\b(FC2[-_]?PPV[-_]?\\d{5,7}|[A-Z]{2,10}[-_]?\\d{2,7})\\b");

    public MetadataResult parse(String fileName) {
        MetadataResult result = MetadataResult.builder().build();

        // Remove extension
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;

        // Try JAV Code extraction (always attempt, populate providerIds)
        String javCode = extractJavCode(baseName);
        if (javCode != null) {
            result.getProviderIds().put("jav_code", javCode);
        }

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

        // If we found a JAV code, use it as the title
        if (javCode != null) {
            result.setTitle(javCode);
            result.setOriginalTitle(javCode);
            return result;
        }

        // Fallback: Just use cleaned filename as title
        result.setTitle(cleanTitle(baseName));
        result.setOriginalTitle(result.getTitle());
        return result;
    }

    /**
     * Extract JAV code from a filename string.
     * Public static method so extractors can call it directly.
     *
     * @param fileName filename or base name to extract from
     * @return normalized JAV code (e.g. "SSIS-456") or null
     */
    public static String extractJavCode(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;

        // Remove extension if present
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx > 0 ? fileName.substring(0, dotIdx) : fileName;

        Matcher matcher = JAV_CODE_PATTERN.matcher(baseName);
        if (matcher.find()) {
            String code = matcher.group(1).toUpperCase();
            // Normalize: ensure dash between letters and numbers
            // e.g. "SSIS456" → "SSIS-456", but keep "FC2-PPV-1234567" as-is
            if (code.startsWith("FC2")) {
                return code.replaceAll("[-_]+", "-"); // normalize separators
            }
            // For standard codes: insert dash if missing
            code = code.replaceAll("[-_]+", "-"); // normalize existing separators
            if (!code.contains("-")) {
                code = code.replaceFirst("(\\d)", "-$1");
            }
            return code;
        }
        return null;
    }

    private String cleanTitle(String raw) {
        return raw.replace('.', ' ')
                  .replace('_', ' ')
                  .trim();
    }
}
