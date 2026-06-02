package com.mediamanager.classification.service;

import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import lombok.extern.slf4j.Slf4j;

/**
 * Safe, whitelist-style matcher for {@link com.mediamanager.classification.entity.ClassificationRule} expressions.
 */
@Slf4j
public final class ClassificationRuleMatcher {

    private static final Pattern COMPARE_PATTERN =
            Pattern.compile("(\\w+)\\s*(>=|<=|>|<|contains|matches)\\s*(.+)", Pattern.CASE_INSENSITIVE);

    /** Maximum allowed regex pattern length to mitigate ReDoS */
    private static final int MAX_REGEX_LENGTH = 200;

    /** Timeout for regex execution (milliseconds) */
    private static final long REGEX_TIMEOUT_MS = 500;

    private static final ExecutorService REGEX_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "regex-guard");
                t.setDaemon(true);
                return t;
            });

    private ClassificationRuleMatcher() {
    }

    public static boolean matchesPath(MediaItem item, MediaFile file, String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        if (file == null || file.getFilePath() == null) {
            return false;
        }
        String expr = expression.trim();
        Matcher m = COMPARE_PATTERN.matcher(expr);
        if (m.matches() && "matches".equalsIgnoreCase(m.group(2))) {
            String regexPattern = m.group(3).trim();
            return safeRegexMatch(file.getFilePath(), regexPattern);
        }
        String needle = extractContainsNeedle(expr);
        String hay = file.getFilePath().toLowerCase(Locale.ROOT);
        return hay.contains(needle.toLowerCase(Locale.ROOT));
    }

    public static boolean matchesMetadata(MediaItem item, String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String expr = expression.trim();
        Matcher m = COMPARE_PATTERN.matcher(expr);
        if (!m.matches()) {
            return expr.length() > 0 && item.getOverview() != null
                    && item.getOverview().toLowerCase(Locale.ROOT).contains(expr.toLowerCase(Locale.ROOT));
        }
        String field = m.group(1).toLowerCase(Locale.ROOT);
        String op = m.group(2).toLowerCase(Locale.ROOT);
        String raw = m.group(3).trim();
        return switch (field) {
            case "year" -> compareYear(item.getReleaseDate(), op, parseInt(raw, 0));
            case "rating" -> compareDouble(item.getRating() != null ? item.getRating().doubleValue() : null, op, parseDouble(raw, 0));
            case "overview" -> {
                if ("matches".equals(op)) {
                    yield item.getOverview() != null && safeRegexMatch(item.getOverview(), raw);
                } else if ("contains".equals(op)) {
                    yield item.getOverview() != null
                            && item.getOverview().toLowerCase(Locale.ROOT).contains(raw.toLowerCase(Locale.ROOT));
                }
                yield false;
            }
            default -> false;
        };
    }

    public static boolean matchesFile(MediaFile file, String expression) {
        if (file == null || expression == null || expression.isBlank()) {
            return false;
        }
        String expr = expression.trim();
        Matcher m = COMPARE_PATTERN.matcher(expr);
        if (!m.matches()) {
            return false;
        }
        String field = m.group(1).toLowerCase(Locale.ROOT);
        String op = m.group(2).toLowerCase(Locale.ROOT);
        String rawValue = m.group(3).trim();

        // Handle regex matching for string-type file fields
        if ("matches".equals(op)) {
            String fieldValue = switch (field) {
                case "path", "filepath" -> file.getFilePath();
                case "videocodec" -> file.getVideoCodec();
                case "audiocodec" -> file.getAudioCodec();
                default -> null;
            };
            return fieldValue != null && safeRegexMatch(fieldValue, rawValue);
        }

        int threshold = parseInt(rawValue, 0);
        return switch (field) {
            case "resolution", "width" -> compareInt(file.getWidth(), op, threshold);
            case "height" -> compareInt(file.getHeight(), op, threshold);
            case "bitrate" -> compareInt(file.getBitrate(), op, threshold);
            default -> false;
        };
    }

    private static String extractContainsNeedle(String expression) {
        Matcher m = COMPARE_PATTERN.matcher(expression);
        if (m.matches() && "contains".equalsIgnoreCase(m.group(2))) {
            return m.group(3).trim();
        }
        if (expression.toLowerCase(Locale.ROOT).startsWith("path contains ")) {
            return expression.substring("path contains ".length()).trim();
        }
        return expression;
    }

    private static boolean compareYear(LocalDate releaseDate, String op, int threshold) {
        if (releaseDate == null) {
            return false;
        }
        return compareInt(releaseDate.getYear(), op, threshold);
    }

    private static boolean compareInt(Integer value, String op, int threshold) {
        if (value == null) {
            return false;
        }
        return switch (op) {
            case ">=" -> value >= threshold;
            case "<=" -> value <= threshold;
            case ">" -> value > threshold;
            case "<" -> value < threshold;
            default -> false;
        };
    }

    private static boolean compareDouble(Double value, String op, double threshold) {
        if (value == null) {
            return false;
        }
        return switch (op) {
            case ">=" -> value >= threshold;
            case "<=" -> value <= threshold;
            case ">" -> value > threshold;
            case "<" -> value < threshold;
            default -> false;
        };
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.replaceAll("[^0-9.-]", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Safely attempts regex matching, returning false on invalid patterns.
     * Applies a length check and a timeout to guard against ReDoS.
     */
    private static boolean safeRegexMatch(String input, String regex) {
        if (regex == null || regex.length() > MAX_REGEX_LENGTH) {
            log.warn("Regex pattern rejected (null or exceeds {} chars): '{}'",
                    MAX_REGEX_LENGTH, regex);
            return false;
        }
        try {
            Pattern compiled = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            Future<Boolean> future = REGEX_EXECUTOR.submit(
                    () -> compiled.matcher(input).find());
            return future.get(REGEX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern in classification rule: '{}' - {}", regex, e.getMessage());
            return false;
        } catch (TimeoutException e) {
            log.warn("Regex match timed out after {}ms for pattern: '{}'", REGEX_TIMEOUT_MS, regex);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            log.warn("Regex execution failed for pattern '{}': {}", regex, e.getMessage());
            return false;
        }
    }
}
