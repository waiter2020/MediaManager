package com.mediamanager.media.service;

import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.service.StoragePathMapper;
import com.mediamanager.media.dto.MediaSubtitleDto;
import com.mediamanager.media.dto.SubtitleSearchResultDto;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.entity.MediaSubtitle;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.media.repository.MediaItemRepository;
import com.mediamanager.media.repository.MediaSubtitleRepository;
import com.mediamanager.media.spi.SubtitleSearchProvider;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaSubtitleService {

    private static final Set<String> SUBTITLE_EXTENSIONS = Set.of("srt", "vtt", "ass", "ssa");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "3gp", "asf", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4",
            "mpg", "mpeg", "mts", "ogv", "ts", "vob", "webm", "wmv"
    );
    private static final Set<String> LANGUAGE_ALIASES = Set.of(
            "zh", "zho", "chi", "chs", "cht", "cn", "sc", "tc", "en", "eng",
            "ja", "jpn", "ko", "kor", "fr", "fre", "fra", "de", "ger", "deu",
            "es", "spa", "it", "ita", "pt", "por", "ru", "rus"
    );
    private static final Set<String> SUBTITLE_FLAGS = Set.of("forced", "sdh", "cc", "default");
    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{[^}]*}");

    private final MediaSubtitleRepository subtitleRepository;
    private final MediaFileRepository fileRepository;
    private final MediaItemRepository itemRepository;
    private final LibraryAccessService libraryAccessService;
    private final StoragePathMapper storagePathMapper;
    private final List<SubtitleSearchProvider> searchProviders;

    @Transactional(readOnly = true)
    public List<MediaSubtitleDto> getSubtitlesForItem(Integer itemId) {
        MediaItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        return subtitleRepository.findByMediaItemIdOrderByLanguageAscFileNameAsc(itemId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MediaSubtitleDto> getSubtitlesForFile(Integer fileId) {
        MediaFile file = fileRepository.findByIdWithItemAndLibrary(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        libraryAccessService.assertCanViewFile(file);
        return subtitleRepository.findByMediaFileIdOrderByLanguageAscFileNameAsc(fileId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubtitleSearchResultDto> searchOnlineSubtitles(Integer itemId, String query, String language) {
        MediaItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ITEM_NOT_FOUND));
        libraryAccessService.assertCanViewItem(item);
        if (searchProviders == null || searchProviders.isEmpty()) {
            return List.of();
        }
        MediaFile primaryFile = fileRepository.findByMediaItemIdAndDeletedFalse(itemId).stream()
                .findFirst()
                .orElse(null);
        String resolvedQuery = query != null && !query.isBlank() ? query : item.getTitle();
        if (resolvedQuery == null || resolvedQuery.isBlank()) {
            resolvedQuery = item.getOriginalTitle();
        }
        SubtitleSearchProvider.SearchContext context =
                new SubtitleSearchProvider.SearchContext(item, primaryFile, resolvedQuery, language);
        List<SubtitleSearchResultDto> results = new ArrayList<>();
        for (SubtitleSearchProvider provider : searchProviders) {
            try {
                List<SubtitleSearchResultDto> providerResults = provider.search(context);
                if (providerResults != null) {
                    results.addAll(providerResults);
                }
            } catch (Exception e) {
                log.warn("Subtitle provider {} failed for item {}: {}", provider.id(), itemId, e.getMessage());
            }
        }
        return results.stream()
                .sorted(Comparator.comparing(SubtitleSearchResultDto::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getSubtitleTrack(Integer subtitleId) throws IOException {
        MediaSubtitle subtitle = subtitleRepository.findById(subtitleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND));
        if (subtitle.getMediaFile() != null) {
            libraryAccessService.assertCanViewFile(subtitle.getMediaFile());
        } else {
            libraryAccessService.assertCanViewItem(subtitle.getMediaItem());
        }

        Path path = Path.of(storagePathMapper.mapPathIfNeeded(subtitle.getFilePath()));
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new BusinessException(ErrorCode.MEDIA_FILE_NOT_FOUND, "Subtitle file not found");
        }

        byte[] body = toWebVtt(path, subtitle.getFormat()).getBytes(StandardCharsets.UTF_8);
        ByteArrayResource resource = new ByteArrayResource(body);
        String fileName = getBaseName(subtitle.getFileName() != null ? subtitle.getFileName() : "subtitle") + ".vtt";
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/vtt;charset=UTF-8"))
                .contentLength(body.length)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @Transactional
    public void syncLocalSubtitles(MediaItem item, MediaFile mediaFile, Path mediaPath) {
        if (item == null || item.getId() == null || mediaFile == null || mediaFile.getId() == null || mediaPath == null) {
            return;
        }
        Path parent = mediaPath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return;
        }
        String mediaBaseName = getBaseName(mediaPath.getFileName().toString());
        Set<String> seenPaths = new HashSet<>();
        for (Path subtitlePath : discoverSubtitleCandidates(parent, mediaBaseName)) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(subtitlePath, BasicFileAttributes.class);
                MediaSubtitle subtitle = upsertLocalSubtitle(item, mediaFile, subtitlePath, attrs, mediaBaseName);
                seenPaths.add(subtitle.getFilePath());
            } catch (Exception e) {
                log.warn("Failed to register local subtitle {}: {}", subtitlePath, e.getMessage());
            }
        }

        List<MediaSubtitle> existingLocal =
                subtitleRepository.findByMediaFileIdAndSourceOrderByLanguageAscFileNameAsc(mediaFile.getId(), "LOCAL");
        for (MediaSubtitle subtitle : existingLocal) {
            if (!seenPaths.contains(subtitle.getFilePath())) {
                subtitleRepository.delete(subtitle);
            }
        }
    }

    @Transactional
    public boolean tryAttachSubtitleFile(Path subtitlePath, BasicFileAttributes attrs) {
        if (subtitlePath == null || !isSubtitleFile(subtitlePath)) {
            return false;
        }
        Path parent = subtitlePath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return false;
        }
        String subtitleBase = getBaseName(subtitlePath.getFileName().toString());
        try (Stream<Path> files = Files.list(parent)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(MediaSubtitleService::isVideoFile)
                    .filter(videoPath -> subtitleMatchesVideo(subtitleBase, getBaseName(videoPath.getFileName().toString())))
                    .map(this::findActiveMediaFile)
                    .flatMap(java.util.Optional::stream)
                    .findFirst()
                    .map(mediaFile -> {
                        upsertLocalSubtitle(
                                mediaFile.getMediaItem(),
                                mediaFile,
                                subtitlePath,
                                attrs,
                                getBaseName(mediaFile.getFileName()));
                        return true;
                    })
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Failed to attach subtitle {}: {}", subtitlePath, e.getMessage());
            return false;
        }
    }

    @Transactional
    public MediaSubtitle upsertLocalSubtitle(
            MediaItem item,
            MediaFile mediaFile,
            Path subtitlePath,
            BasicFileAttributes attrs,
            String mediaBaseName) {
        String filePath = normalizePath(subtitlePath.toAbsolutePath().toString());
        String fileName = subtitlePath.getFileName().toString();
        String format = getExtension(fileName).toLowerCase(Locale.ROOT);
        String language = inferLanguage(fileName, mediaBaseName);
        boolean forced = fileName.toLowerCase(Locale.ROOT).contains("forced");

        MediaSubtitle subtitle = subtitleRepository.findByFilePath(filePath).orElseGet(MediaSubtitle::new);
        subtitle.setMediaItem(item);
        subtitle.setMediaFile(mediaFile);
        subtitle.setFilePath(filePath);
        subtitle.setFileName(fileName);
        subtitle.setFormat(format);
        subtitle.setLanguage(language);
        subtitle.setTitle(buildSubtitleTitle(language, forced));
        subtitle.setSource("LOCAL");
        subtitle.setFileSize(attrs != null ? attrs.size() : null);
        subtitle.setFileModifiedAt(attrs != null ? attrs.lastModifiedTime().toInstant() : null);
        subtitle.setForced(forced);
        if (subtitle.getDefaultTrack() == null) {
            subtitle.setDefaultTrack(false);
        }
        return subtitleRepository.save(subtitle);
    }

    public MediaSubtitleDto toDto(MediaSubtitle subtitle) {
        return MediaSubtitleDto.builder()
                .id(subtitle.getId())
                .mediaItemId(subtitle.getMediaItem() != null ? subtitle.getMediaItem().getId() : null)
                .mediaFileId(subtitle.getMediaFile() != null ? subtitle.getMediaFile().getId() : null)
                .fileName(subtitle.getFileName())
                .language(subtitle.getLanguage())
                .format(subtitle.getFormat())
                .title(subtitle.getTitle())
                .source(subtitle.getSource())
                .provider(subtitle.getProvider())
                .externalId(subtitle.getExternalId())
                .fileSize(subtitle.getFileSize())
                .defaultTrack(subtitle.getDefaultTrack())
                .forced(subtitle.getForced())
                .build();
    }

    public static boolean isSubtitleFile(Path path) {
        return path != null && SUBTITLE_EXTENSIONS.contains(getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT));
    }

    private List<Path> discoverSubtitleCandidates(Path parent, String mediaBaseName) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        collectMatchingSubtitles(parent, mediaBaseName, candidates);
        for (String dirName : List.of("Subs", "subs", "Subtitles", "subtitles")) {
            Path subtitleDir = parent.resolve(dirName);
            if (Files.isDirectory(subtitleDir)) {
                collectMatchingSubtitles(subtitleDir, mediaBaseName, candidates);
            }
        }
        return new ArrayList<>(candidates);
    }

    private void collectMatchingSubtitles(Path directory, String mediaBaseName, Set<Path> candidates) {
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(MediaSubtitleService::isSubtitleFile)
                    .filter(path -> subtitleMatchesVideo(getBaseName(path.getFileName().toString()), mediaBaseName))
                    .sorted()
                    .forEach(path -> candidates.add(path.toAbsolutePath().normalize()));
        } catch (IOException e) {
            log.debug("Failed to list subtitle directory {}", directory, e);
        }
    }

    private java.util.Optional<MediaFile> findActiveMediaFile(Path videoPath) {
        String filePath = normalizePath(videoPath.toAbsolutePath().toString());
        return fileRepository.findByFilePath(filePath)
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .filter(file -> file.getMediaItem() != null);
    }

    private String toWebVtt(Path path, String format) throws IOException {
        String fmt = format != null ? format.toLowerCase(Locale.ROOT) : getExtension(path.getFileName().toString());
        String text = readSubtitleText(path);
        return switch (fmt) {
            case "vtt" -> ensureWebVttHeader(text);
            case "ass", "ssa" -> convertAssToVtt(text);
            default -> convertSrtToVtt(text);
        };
    }

    private String readSubtitleText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    private String ensureWebVttHeader(String text) {
        String normalized = stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
        return normalized.startsWith("WEBVTT") ? normalized : "WEBVTT\n\n" + normalized;
    }

    private String convertSrtToVtt(String text) {
        String normalized = stripBom(text).replace("\r\n", "\n").replace('\r', '\n').trim();
        StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
        for (String block : normalized.split("\\n\\s*\\n")) {
            String[] lines = block.split("\\n");
            if (lines.length == 0) {
                continue;
            }
            int startIndex = lines[0].trim().matches("\\d+") ? 1 : 0;
            if (startIndex >= lines.length || !lines[startIndex].contains("-->")) {
                continue;
            }
            vtt.append(lines[startIndex].replace(',', '.').trim()).append('\n');
            for (int i = startIndex + 1; i < lines.length; i++) {
                vtt.append(lines[i]).append('\n');
            }
            vtt.append('\n');
        }
        return vtt.toString();
    }

    private String convertAssToVtt(String text) {
        String normalized = stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder vtt = new StringBuilder("WEBVTT\n\n");
        List<String> fields = new ArrayList<>();
        boolean inEvents = false;
        for (String rawLine : normalized.split("\\n")) {
            String line = rawLine.trim();
            if (line.equalsIgnoreCase("[Events]")) {
                inEvents = true;
                continue;
            }
            if (!inEvents) {
                continue;
            }
            if (line.startsWith("[")) {
                break;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("format:")) {
                fields = Stream.of(line.substring(line.indexOf(':') + 1).split(","))
                        .map(String::trim)
                        .toList();
                continue;
            }
            if (!line.toLowerCase(Locale.ROOT).startsWith("dialogue:") || fields.isEmpty()) {
                continue;
            }
            String payload = line.substring(line.indexOf(':') + 1).trim();
            String[] values = payload.split(",", fields.size());
            Map<String, String> map = new java.util.HashMap<>();
            for (int i = 0; i < Math.min(fields.size(), values.length); i++) {
                map.put(fields.get(i).toLowerCase(Locale.ROOT), values[i].trim());
            }
            String start = map.get("start");
            String end = map.get("end");
            String cueText = map.get("text");
            if (start == null || end == null || cueText == null) {
                continue;
            }
            cueText = ASS_OVERRIDE.matcher(cueText)
                    .replaceAll("")
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .trim();
            if (!cueText.isBlank()) {
                vtt.append(toVttTimestamp(start)).append(" --> ").append(toVttTimestamp(end)).append('\n')
                        .append(cueText).append("\n\n");
            }
        }
        return vtt.toString();
    }

    private String toVttTimestamp(String assTimestamp) {
        String[] parts = assTimestamp.trim().split(":");
        if (parts.length != 3) {
            return assTimestamp.replace(',', '.');
        }
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        String[] secParts = parts[2].split("\\.");
        int seconds = Integer.parseInt(secParts[0]);
        String millis = secParts.length > 1 ? secParts[1] : "0";
        if (millis.length() == 1) {
            millis = millis + "00";
        } else if (millis.length() == 2) {
            millis = millis + "0";
        } else if (millis.length() > 3) {
            millis = millis.substring(0, 3);
        }
        return String.format("%02d:%02d:%02d.%s", hours, minutes, seconds, millis);
    }

    private static boolean isVideoFile(Path path) {
        return path != null && VIDEO_EXTENSIONS.contains(getExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT));
    }

    private static boolean subtitleMatchesVideo(String subtitleBase, String mediaBaseName) {
        if (subtitleBase.equals(mediaBaseName)) {
            return true;
        }
        String lower = subtitleBase.toLowerCase(Locale.ROOT);
        String mediaLower = mediaBaseName.toLowerCase(Locale.ROOT);
        if (lower.startsWith(mediaLower + ".")
                || lower.startsWith(mediaLower + "-")
                || lower.startsWith(mediaLower + "_")
                || lower.startsWith(mediaLower + " ")) {
            return true;
        }
        return subtitleBaseCandidates(subtitleBase).contains(mediaBaseName);
    }

    private static Set<String> subtitleBaseCandidates(String subtitleBase) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(subtitleBase);
        String current = subtitleBase;
        while (true) {
            int cut = Math.max(
                    Math.max(current.lastIndexOf('.'), current.lastIndexOf('-')),
                    Math.max(current.lastIndexOf('_'), current.lastIndexOf(' '))
            );
            if (cut <= 0) {
                break;
            }
            String suffix = current.substring(cut + 1).toLowerCase(Locale.ROOT);
            if (!LANGUAGE_ALIASES.contains(suffix) && !SUBTITLE_FLAGS.contains(suffix)) {
                break;
            }
            current = current.substring(0, cut);
            candidates.add(current);
        }
        return candidates;
    }

    private static String inferLanguage(String fileName, String mediaBaseName) {
        String base = getBaseName(fileName);
        String suffix = base;
        if (mediaBaseName != null && !mediaBaseName.isBlank()
                && base.toLowerCase(Locale.ROOT).startsWith(mediaBaseName.toLowerCase(Locale.ROOT))) {
            suffix = base.substring(Math.min(base.length(), mediaBaseName.length()));
        }
        for (String token : suffix.split("[._\\-\\s]+")) {
            String normalized = normalizeLanguage(token);
            if (normalized != null) {
                return normalized;
            }
        }
        return "und";
    }

    private static String normalizeLanguage(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "zh", "zho", "chi", "chs", "cn", "sc" -> "zh-CN";
            case "cht", "tc" -> "zh-TW";
            case "en", "eng" -> "en";
            case "ja", "jpn" -> "ja";
            case "ko", "kor" -> "ko";
            case "fr", "fre", "fra" -> "fr";
            case "de", "ger", "deu" -> "de";
            case "es", "spa" -> "es";
            case "it", "ita" -> "it";
            case "pt", "por" -> "pt";
            case "ru", "rus" -> "ru";
            default -> null;
        };
    }

    private static String buildSubtitleTitle(String language, boolean forced) {
        String label = language != null && !"und".equals(language) ? language : "Subtitle";
        return forced ? label + " Forced" : label;
    }

    private static String stripBom(String text) {
        return text != null && text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static String getBaseName(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }

    private static String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot == -1 ? "" : fileName.substring(dot + 1);
    }

    private static String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }
}
