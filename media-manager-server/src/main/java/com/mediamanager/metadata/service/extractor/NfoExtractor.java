package com.mediamanager.metadata.service.extractor;

import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NfoExtractor implements MetadataExtractor {

    // Simple robust regex for NFO basic fields (Jellyfin/Kodi compatible)
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORIGINAL_TITLE_PATTERN = Pattern.compile("<originaltitle>(.*?)</originaltitle>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLOT_PATTERN = Pattern.compile("<plot>(.*?)</plot>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String getType() {
        return "NFO";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryPluginConfig config) {
        if (context.primaryFile() == null) return null;
        String videoPathStr = context.primaryFile().getFilePath();
        Path videoPath = Paths.get(videoPathStr);
        String fileNameWithoutExt = getFileNameWithoutExtension(videoPath);
        
        // Conventional NFO path: Same directory, same base name + .nfo
        Path nfoPath = videoPath.getParent().resolve(fileNameWithoutExt + ".nfo");
        Path movieNfoPath = videoPath.getParent().resolve("movie.nfo");
        Path tvShowNfoPath = videoPath.getParent().resolve("tvshow.nfo");

        Path targetNfo = null;
        if (Files.exists(nfoPath)) targetNfo = nfoPath;
        else if (Files.exists(movieNfoPath)) targetNfo = movieNfoPath;
        else if (Files.exists(tvShowNfoPath)) targetNfo = tvShowNfoPath;

        if (targetNfo == null) {
            log.debug("No NFO file found for {}", videoPath);
            return null;
        }

        try {
            String nfoContent = Files.readString(targetNfo);
            MetadataResult result = MetadataResult.builder().build();

            Matcher titleMatcher = TITLE_PATTERN.matcher(nfoContent);
            if (titleMatcher.find()) result.setTitle(titleMatcher.group(1).trim());

            Matcher origTitleMatcher = ORIGINAL_TITLE_PATTERN.matcher(nfoContent);
            if (origTitleMatcher.find()) result.setOriginalTitle(origTitleMatcher.group(1).trim());

            Matcher plotMatcher = PLOT_PATTERN.matcher(nfoContent);
            if (plotMatcher.find()) result.setOverview(plotMatcher.group(1).trim());

            // Real impl would parse full XML using Jackson XML or standard Dom
            log.info("Extracted NFO for {} -> Title: {}", targetNfo.getFileName(), result.getTitle());
            return result;
        } catch (IOException e) {
            log.error("Failed to read NFO file: {}", targetNfo, e);
        }

        return null;
    }

    private String getFileNameWithoutExtension(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }
}
