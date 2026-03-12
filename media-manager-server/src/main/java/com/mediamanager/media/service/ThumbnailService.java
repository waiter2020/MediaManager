package com.mediamanager.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates video thumbnails using ffmpeg.
 * Extracts a single frame at ~10% of the video duration (or 10s) and saves it as JPEG.
 */
@Slf4j
@Service
public class ThumbnailService {

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${mediamanager.data.thumbnail-dir:./data/cache/thumbnails}")
    private String thumbnailDir;

    /**
     * Generate a thumbnail for a video file.
     *
     * @param mediaItemId ID of the media item (used for filename)
     * @param videoFilePath absolute path to the video file
     * @return absolute path to the generated thumbnail, or null on failure
     */
    public String generateThumbnail(Integer mediaItemId, String videoFilePath) {
        try {
            // Ensure thumbnail directory exists
            Path dir = Path.of(thumbnailDir);
            Files.createDirectories(dir);

            String outputFile = dir.resolve(mediaItemId + ".jpg").toAbsolutePath().toString();

            // Skip if thumbnail already exists
            if (new File(outputFile).exists()) {
                log.debug("Thumbnail already exists for item {}", mediaItemId);
                return outputFile;
            }

            // Extract a frame at 10 seconds into the video
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-ss", "10",
                    "-i", videoFilePath,
                    "-vframes", "1",
                    "-vf", "scale=480:-1",
                    "-q:v", "6",
                    "-y",
                    outputFile
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            // Consume output to prevent buffer deadlock
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.warn("ffmpeg thumbnail generation exited with code {} for item {}", exitCode, mediaItemId);
                // Try again at 1 second for very short videos
                return retryAtOneSecond(mediaItemId, videoFilePath, outputFile);
            }

            if (new File(outputFile).exists() && new File(outputFile).length() > 0) {
                log.info("Generated thumbnail for item {}: {}", mediaItemId, outputFile);
                return outputFile;
            }

            log.warn("Thumbnail file not created for item {}", mediaItemId);
            return null;

        } catch (IOException e) {
            log.error("Failed to generate thumbnail for item {}", mediaItemId, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thumbnail generation interrupted for item {}", mediaItemId, e);
            return null;
        }
    }

    private String retryAtOneSecond(Integer mediaItemId, String videoFilePath, String outputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-ss", "1",
                    "-i", videoFilePath,
                    "-vframes", "1",
                    "-vf", "scale=480:-1",
                    "-q:v", "6",
                    "-y",
                    outputFile
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();

            if (exitCode == 0 && new File(outputFile).exists() && new File(outputFile).length() > 0) {
                log.info("Generated thumbnail (retry at 1s) for item {}", mediaItemId);
                return outputFile;
            }
        } catch (Exception e) {
            log.error("Retry thumbnail generation failed for item {}", mediaItemId, e);
        }
        return null;
    }
}
