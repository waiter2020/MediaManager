package com.mediamanager.metadata.util;

import com.mediamanager.metadata.spi.MetadataResult;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class FileNameParserTest {

    private final FileNameParser parser = new FileNameParser();

    @Test
    void testParseMovieWithYear() {
        MetadataResult result = parser.parse("Inception (2010).mp4");
        assertEquals("Inception", result.getTitle());
        assertEquals("Inception", result.getOriginalTitle());
        assertEquals(LocalDate.of(2010, 1, 1), result.getReleaseDate());
    }

    @Test
    void testParseTvEpisode() {
        MetadataResult result = parser.parse("Breaking.Bad.S01E03.mkv");
        assertEquals("Breaking Bad", result.getTitle());
        assertEquals(1, result.getSeasonNumber());
        assertEquals(3, result.getEpisodeNumber());
    }

    @Test
    void testParseTvEpisodeAlt() {
        MetadataResult result = parser.parse("Friends.10x12.mp4");
        assertEquals("Friends", result.getTitle());
        assertEquals(10, result.getSeasonNumber());
        assertEquals(12, result.getEpisodeNumber());
    }

    @Test
    void testParseTvEpisodeDash() {
        MetadataResult result = parser.parse("The Simpsons - 12.mp4");
        assertEquals("The Simpsons", result.getTitle());
        assertEquals(1, result.getSeasonNumber());
        assertEquals(12, result.getEpisodeNumber());
    }

    @Test
    void testParseJavCode() {
        MetadataResult result = parser.parse("ABP-123.mp4");
        assertEquals("ABP-123", result.getTitle());
        assertEquals("ABP-123", result.getProviderIds().get("jav_code"));

        MetadataResult result2 = parser.parse("ssis456.mkv");
        assertEquals("SSIS-456", result2.getTitle());
        assertEquals("SSIS-456", result2.getProviderIds().get("jav_code"));
    }

    @Test
    void testParseDateTitle() {
        MetadataResult result = parser.parse("2021.05.30.Daily.Show.mp4");
        assertEquals("Daily Show", result.getTitle());
        assertEquals(LocalDate.of(2021, 5, 30), result.getReleaseDate());
    }

    @Test
    void testExtractJavCodeStatic() {
        assertEquals("ABP-123", FileNameParser.extractJavCode("ABP-123.mp4"));
        assertEquals("SSIS-456", FileNameParser.extractJavCode("SSIS456.mkv"));
        assertEquals("FC2-PPV-1234567", FileNameParser.extractJavCode("FC2-PPV-1234567.mp4"));
        assertNull(FileNameParser.extractJavCode("SomeMovie.mp4"));
    }
}
