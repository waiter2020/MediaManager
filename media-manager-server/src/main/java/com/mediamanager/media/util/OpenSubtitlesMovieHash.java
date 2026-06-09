package com.mediamanager.media.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OpenSubtitlesMovieHash {

    private static final int CHUNK_SIZE = 64 * 1024;

    private OpenSubtitlesMovieHash() {
    }

    public static String compute(Path file) throws IOException {
        long size = Files.size(file);
        if (size < 128 * 1024) {
            return null;
        }
        byte[] buffer = new byte[CHUNK_SIZE * 2];
        try (InputStream input = Files.newInputStream(file)) {
            int firstRead = input.read(buffer, 0, CHUNK_SIZE);
            if (firstRead <= 0) {
                return null;
            }
            long skip = Math.max(0, size - CHUNK_SIZE);
            if (input.skip(skip) < skip) {
                return null;
            }
            int lastRead = input.read(buffer, CHUNK_SIZE, CHUNK_SIZE);
            if (lastRead <= 0) {
                return null;
            }
        }
        long hash = size;
        for (int i = 0; i < CHUNK_SIZE + CHUNK_SIZE; i += 8) {
            if (i + 8 > buffer.length) {
                break;
            }
            long value = ByteBuffer.wrap(buffer, i, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
            hash += value;
            hash &= 0xFFFFFFFFFFFFFFFFL;
        }
        return String.format("%016x", hash);
    }
}
