package com.mediamanager.metadata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.entity.Tag;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.metadata.repository.MovieMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NfoExportServiceTest {

    @Mock
    private MediaFileRepository fileRepository;

    @Mock
    private MovieMetadataRepository movieMetadataRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NfoExportService nfoExportService;

    @TempDir
    Path tempDir;

    private MediaItem movieItem;
    private MediaItem tvShowItem;
    private MediaFile mediaFile;
    private Path videoFilePath;

    @BeforeEach
    void setUp() throws IOException {
        videoFilePath = tempDir.resolve("movie_file.mkv");
        Files.writeString(videoFilePath, "dummy content");

        movieItem = MediaItem.builder()
                .id(1)
                .title("Interstellar")
                .originalTitle("Interstellar Original")
                .overview("A team of explorers travel through a wormhole in space.")
                .type("MOVIE")
                .rating(java.math.BigDecimal.valueOf(8.6))
                .releaseDate(LocalDate.of(2014, 11, 7))
                .providerIds("{\"tmdb\":\"157336\",\"imdb\":\"tt0816692\"}")
                .build();

        tvShowItem = MediaItem.builder()
                .id(2)
                .title("Breaking Bad")
                .originalTitle("Breaking Bad Original")
                .overview("A high school chemistry teacher turns to manufacturing methamphetamine.")
                .type("TV_SHOW")
                .rating(java.math.BigDecimal.valueOf(9.5))
                .releaseDate(LocalDate.of(2008, 1, 20))
                .providerIds("{\"tmdb\":\"1396\"}")
                .build();

        mediaFile = MediaFile.builder()
                .id(100)
                .mediaItem(movieItem)
                .filePath(videoFilePath.toAbsolutePath().toString())
                .fileName("movie_file.mkv")
                .deleted(false)
                .build();
    }

    @Test
    void exportMovieNfoSuccess() throws IOException {
        when(fileRepository.findByMediaItemIdAndDeletedFalse(1)).thenReturn(List.of(mediaFile));
        when(movieMetadataRepository.findByMediaItemId(1)).thenReturn(Optional.empty());

        // Add categories and tags
        Category category = new Category();
        category.setName("Sci-Fi");
        movieItem.setCategories(Set.of(category));

        Tag tag = new Tag();
        tag.setName("Space Travel");
        movieItem.setTags(Set.of(tag));

        nfoExportService.export(movieItem);

        Path expectedNfoPath = tempDir.resolve("movie_file.nfo");
        assertTrue(Files.exists(expectedNfoPath));

        String content = Files.readString(expectedNfoPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("<movie>"));
        assertTrue(content.contains("<title>Interstellar</title>"));
        assertTrue(content.contains("<originaltitle>Interstellar Original</originaltitle>"));
        assertTrue(content.contains("<plot>A team of explorers travel through a wormhole in space.</plot>"));
        assertTrue(content.contains("<rating>8.6</rating>"));
        assertTrue(content.contains("<releasedate>2014-11-07</releasedate>"));
        assertTrue(content.contains("<year>2014</year>"));
        assertTrue(content.contains("<uniqueid default=\"true\" type=\"tmdb\">157336</uniqueid>"));
        assertTrue(content.contains("<uniqueid default=\"false\" type=\"imdb\">tt0816692</uniqueid>"));
        assertTrue(content.contains("<genre>Sci-Fi</genre>"));
        assertTrue(content.contains("<tag>Space Travel</tag>"));
        assertTrue(content.contains("</movie>"));
    }

    @Test
    void exportTvShowNfoSuccess() throws IOException {
        mediaFile.setMediaItem(tvShowItem);
        when(fileRepository.findByMediaItemIdAndDeletedFalse(2)).thenReturn(List.of(mediaFile));

        nfoExportService.export(tvShowItem);

        Path expectedNfoPath = tempDir.resolve("movie_file.nfo");
        assertTrue(Files.exists(expectedNfoPath));

        String content = Files.readString(expectedNfoPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("<tvshow>"));
        assertTrue(content.contains("<title>Breaking Bad</title>"));
        assertTrue(content.contains("<rating>9.5</rating>"));
        assertTrue(content.contains("<releasedate>2008-01-20</releasedate>"));
        assertTrue(content.contains("<year>2008</year>"));
        assertTrue(content.contains("<uniqueid default=\"true\" type=\"tmdb\">1396</uniqueid>"));
        assertTrue(content.contains("</tvshow>"));
    }

    @Test
    void exportWithInvalidItemTypeDoesNothing() {
        movieItem.setType("MUSIC");
        nfoExportService.export(movieItem);
        verifyNoInteractions(fileRepository);
    }

    @Test
    void exportWithNullItemDoesNothing() {
        nfoExportService.export(null);
        verifyNoInteractions(fileRepository);
    }

    @Test
    void exportWithNoFilesDoesNothing() {
        when(fileRepository.findByMediaItemIdAndDeletedFalse(1)).thenReturn(Collections.emptyList());
        nfoExportService.export(movieItem);
        Path expectedNfoPath = tempDir.resolve("movie_file.nfo");
        assertFalse(Files.exists(expectedNfoPath));
    }

    @Test
    void exportWithBlankFilePathDoesNothing() {
        mediaFile.setFilePath("");
        when(fileRepository.findByMediaItemIdAndDeletedFalse(1)).thenReturn(List.of(mediaFile));
        nfoExportService.export(movieItem);
        Path expectedNfoPath = tempDir.resolve("movie_file.nfo");
        assertFalse(Files.exists(expectedNfoPath));
    }
}
