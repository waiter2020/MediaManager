package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.repository.MediaFileRepository;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FfprobeExtractor implements MetadataExtractor {

    private final ObjectMapper objectMapper;
    private final MediaFileRepository mediaFileRepository;

    @Value("${mediamanager.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    @Value("${mediamanager.ffprobe.path:ffprobe}")
    private String ffprobePath;

    @Override
    public String getType() {
        return "FFPROBE";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryExtractorConfig config) {
        if (context.primaryFile() == null) return null;
        String filePath = context.primaryFile().getFilePath();
        File file = new File(filePath);
        if (!file.exists()) return null;

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobePath,
                    "-v", "quiet",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    filePath
            );
            // Merge stderr into stdout to prevent buffer-full deadlock
            pb.redirectErrorStream(true);

            Process process = pb.start();
            JsonNode rootNode = objectMapper.readTree(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("ffprobe exited with code {} for {}", exitCode, filePath);
            }

            MetadataResult result = MetadataResult.builder().build();
            MediaFile mediaFile = context.primaryFile();
            boolean mediaFileUpdated = false;

            JsonNode formatNode = rootNode.path("format");
            if (!formatNode.isMissingNode()) {
                if (formatNode.has("duration")) {
                    double seconds = formatNode.get("duration").asDouble();
                    result.setRuntimeMinutes((int) Math.round(seconds / 60.0));
                    mediaFile.setDurationSeconds((int) Math.round(seconds));
                    mediaFileUpdated = true;
                }
                if (formatNode.has("bit_rate")) {
                    mediaFile.setBitrate(formatNode.get("bit_rate").asInt());
                    mediaFileUpdated = true;
                }

                JsonNode tagsNode = formatNode.path("tags");
                if (!tagsNode.isMissingNode()) {
                    if (tagsNode.has("title")) result.setTitle(tagsNode.get("title").asText());
                    if (tagsNode.has("artist")) result.setArtist(tagsNode.get("artist").asText());
                    if (tagsNode.has("album")) result.setAlbum(tagsNode.get("album").asText());
                    if (tagsNode.has("date")) {
                        try {
                            String dateStr = tagsNode.get("date").asText();
                            if (dateStr.length() == 4) {
                                result.setReleaseDate(java.time.LocalDate.parse(dateStr + "-01-01"));
                            } else if (dateStr.length() >= 10) {
                                result.setReleaseDate(java.time.LocalDate.parse(dateStr.substring(0, 10)));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            JsonNode streamsNode = rootNode.path("streams");
            if (streamsNode.isArray()) {
                for (JsonNode stream : streamsNode) {
                    String codecType = stream.path("codec_type").asText("");
                    switch (codecType) {
                        case "video" -> {
                            if (mediaFile.getVideoCodec() == null && stream.has("codec_name")) {
                                mediaFile.setVideoCodec(stream.get("codec_name").asText());
                                mediaFileUpdated = true;
                            }
                            if (mediaFile.getWidth() == null && stream.has("width")) {
                                mediaFile.setWidth(stream.get("width").asInt());
                                mediaFileUpdated = true;
                            }
                            if (mediaFile.getHeight() == null && stream.has("height")) {
                                mediaFile.setHeight(stream.get("height").asInt());
                                mediaFileUpdated = true;
                            }
                        }
                        case "audio" -> {
                            if (mediaFile.getAudioCodec() == null && stream.has("codec_name")) {
                                mediaFile.setAudioCodec(stream.get("codec_name").asText());
                                mediaFileUpdated = true;
                            }
                        }
                    }
                }
            }

            if (mediaFileUpdated) {
                mediaFileRepository.save(mediaFile);
                log.debug("Updated MediaFile technical fields for {}", filePath);
            }

            return result;
        } catch (IOException e) {
            log.error("Failed to run ffprobe on {}", filePath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ffprobe process interrupted for {}", filePath, e);
        }

        return null;
    }
}
