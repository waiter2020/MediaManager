package com.mediamanager.metadata.service.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.mediamanager.plugin.entity.LibraryPluginConfig;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExifExtractor implements MetadataExtractor {

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "EXIF";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryPluginConfig config) {
        if (context.primaryFile() == null || !"IMAGE".equals(context.mediaItem().getType())) {
            return null;
        }

        String filePath = context.primaryFile().getFilePath();
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            MetadataResult result = MetadataResult.builder().build();
            result.setTitle(context.primaryFile().getFileName());
            Map<String, String> rawExif = new LinkedHashMap<>();

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                if (ifd0.containsTag(ExifIFD0Directory.TAG_MAKE)) {
                    result.setCameraMake(ifd0.getString(ExifIFD0Directory.TAG_MAKE));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_MODEL)) {
                    result.setCameraModel(ifd0.getString(ExifIFD0Directory.TAG_MODEL));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_WIDTH)) {
                    result.setWidth(ifd0.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_HEIGHT)) {
                    result.setHeight(ifd0.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT));
                }
            }

            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_LENS_MODEL)) {
                    result.setLens(subIfd.getString(ExifSubIFDDirectory.TAG_LENS_MODEL));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)) {
                    result.setIso(subIfd.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                    result.setAperture("f/" + subIfd.getString(ExifSubIFDDirectory.TAG_FNUMBER));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
                    result.setShutterSpeed(subIfd.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME) + "s");
                }
                Date takenDate = subIfd.getDateOriginal();
                if (takenDate != null) {
                    result.setTakenAt(takenDate.toInstant());
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
                    result.setWidth(subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
                    result.setHeight(subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT));
                }
            }

            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null && gpsDir.getGeoLocation() != null) {
                result.setGpsLatitude(gpsDir.getGeoLocation().getLatitude());
                result.setGpsLongitude(gpsDir.getGeoLocation().getLongitude());
            }

            for (Directory directory : metadata.getDirectories()) {
                directory.getTags().forEach(tag ->
                        rawExif.put(directory.getName() + "." + tag.getTagName(), tag.getDescription()));
            }
            if (!rawExif.isEmpty()) {
                result.setExifData(objectMapper.writeValueAsString(rawExif));
            }

            log.info("EXIF data extracted for image: {}", filePath);
            return result;
        } catch (Exception e) {
            log.error("Failed to extract EXIF data from {}", filePath, e);
            MetadataResult fallback = MetadataResult.builder().build();
            fallback.setTitle(context.primaryFile().getFileName());
            return fallback;
        }
    }
}
