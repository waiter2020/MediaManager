package com.mediamanager.metadata.service.extractor;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.mediamanager.library.entity.LibraryExtractorConfig;
import com.mediamanager.metadata.entity.ImageMetadata;
import com.mediamanager.metadata.spi.MetadataExtractor;
import com.mediamanager.metadata.spi.MetadataResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class ExifExtractor implements MetadataExtractor {

    @Override
    public String getType() {
        return "EXIF";
    }

    @Override
    public MetadataResult extract(ExtractorContext context, LibraryExtractorConfig config) {
        if (!"IMAGE".equals(context.mediaItem().getType())) {
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

            ImageMetadata.ImageMetadataBuilder imgBuilder = ImageMetadata.builder()
                    .mediaItem(context.mediaItem());

            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                if (ifd0.containsTag(ExifIFD0Directory.TAG_MAKE)) {
                    imgBuilder.cameraMake(ifd0.getString(ExifIFD0Directory.TAG_MAKE));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_MODEL)) {
                    imgBuilder.cameraModel(ifd0.getString(ExifIFD0Directory.TAG_MODEL));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_WIDTH)) {
                    imgBuilder.width(ifd0.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH));
                }
                if (ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_HEIGHT)) {
                    imgBuilder.height(ifd0.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT));
                }
            }

            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfd != null) {
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_LENS_MODEL)) {
                    imgBuilder.lens(subIfd.getString(ExifSubIFDDirectory.TAG_LENS_MODEL));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)) {
                    imgBuilder.iso(subIfd.getString(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                    imgBuilder.aperture("f/" + subIfd.getString(ExifSubIFDDirectory.TAG_FNUMBER));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
                    imgBuilder.shutterSpeed(subIfd.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME) + "s");
                }
                Date takenDate = subIfd.getDateOriginal();
                if (takenDate != null) {
                    imgBuilder.takenAt(takenDate.toInstant());
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
                    imgBuilder.width(subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH));
                }
                if (subIfd.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
                    imgBuilder.height(subIfd.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT));
                }
            }

            GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDir != null && gpsDir.getGeoLocation() != null) {
                imgBuilder.gpsLatitude(gpsDir.getGeoLocation().getLatitude());
                imgBuilder.gpsLongitude(gpsDir.getGeoLocation().getLongitude());
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
