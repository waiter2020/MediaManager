package com.mediamanager.media.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSubtitlesMovieHashTest {

    @TempDir
    Path tempDir;

    @Test
    void computeReturnsNullForSmallFiles() throws Exception {
        Path small = tempDir.resolve("small.mkv");
        Files.write(small, new byte[1024]);
        assertThat(OpenSubtitlesMovieHash.compute(small)).isNull();
    }

    @Test
    void computeReturnsStableHashForLargeFile() throws Exception {
        Path file = tempDir.resolve("movie.mkv");
        byte[] content = new byte[256 * 1024];
        for (int i = 0; i < content.length; i += 1) {
            content[i] = (byte) (i % 251);
        }
        Files.write(file, content);
        String hash = OpenSubtitlesMovieHash.compute(file);
        assertThat(hash).isNotBlank();
        assertThat(hash).matches("[0-9a-f]{16}");
        assertThat(OpenSubtitlesMovieHash.compute(file)).isEqualTo(hash);
    }
}
