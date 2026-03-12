package com.mediamanager.classification.service.strategy;

import com.mediamanager.classification.entity.Category;
import com.mediamanager.classification.repository.CategoryRepository;
import com.mediamanager.classification.spi.ClassifierStrategy;
import com.mediamanager.media.entity.MediaFile;
import com.mediamanager.media.entity.MediaItem;
import com.mediamanager.media.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedClassifier implements ClassifierStrategy {

    private final MediaFileRepository fileRepo;
    private final CategoryRepository categoryRepo;

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public void classify(MediaItem item) {
        // Attempt classification based on physical file traits
        fileRepo.findByMediaItemIdAndDeletedFalse(item.getId()).stream()
                .findFirst()
                .ifPresent(file -> {
                    classifyResolution(item, file);
                    classifyCodec(item, file);
                });
    }

    private void classifyResolution(MediaItem item, MediaFile file) {
        if (file.getWidth() == null || file.getHeight() == null) return;
        
        int width = file.getWidth();
        String resolution = null;
        if (width >= 3800) resolution = "4K";
        else if (width >= 1900) resolution = "1080p";
        else if (width >= 1200) resolution = "720p";
        else resolution = "SD";
        
        String finalRes = resolution;
        
        List<Category> resRoots = categoryRepo.findByType("RESOLUTION");
        if (resRoots.isEmpty()) return;
        Category root = resRoots.get(0);

        Category cat = categoryRepo.findByParentIdAndName(root.getId(), finalRes)
                .orElseGet(() -> categoryRepo.save(Category.builder()
                        .name(finalRes)
                        .parentId(root.getId())
                        .type("RESOLUTION")
                        .build()));

        item.getCategories().add(cat);
    }
    
    private void classifyCodec(MediaItem item, MediaFile file) {
        if (file.getVideoCodec() == null) return;
        
        String codec = file.getVideoCodec().toLowerCase();
        String readable = codec;
        if (codec.contains("hevc") || codec.contains("h265")) readable = "HEVC/H.265";
        else if (codec.contains("h264") || codec.contains("avc")) readable = "AVC/H.264";
        
        String finalCodec = readable;
        
        List<Category> codecRoots = categoryRepo.findByType("CODEC");
        if (codecRoots.isEmpty()) return;
        Category root = codecRoots.get(0);

        Category cat = categoryRepo.findByParentIdAndName(root.getId(), finalCodec)
                .orElseGet(() -> categoryRepo.save(Category.builder()
                        .name(finalCodec)
                        .parentId(root.getId())
                        .type("CODEC")
                        .build()));

        item.getCategories().add(cat);
    }
}
