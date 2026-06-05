package com.mediamanager.classification.service;

import com.mediamanager.classification.entity.Tag;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TagColorService {

    private static final String AI_DEFAULT_PURPLE = "#8b5cf6";
    private static final String[] PALETTE = {
            "#2563eb", "#0891b2", "#059669", "#65a30d", "#ca8a04",
            "#dc2626", "#db2777", "#7c3aed", "#4f46e5", "#0d9488",
            "#16a34a", "#a16207", "#e11d48", "#9333ea", "#0284c7",
            "#15803d", "#b45309", "#be123c"
    };

    public String colorFor(String tagName) {
        String key = tagName == null ? "" : tagName.strip().toLowerCase(Locale.ROOT);
        int hash = key.hashCode() & 0x7fffffff;
        return PALETTE[hash % PALETTE.length];
    }

    public boolean shouldRecolor(Tag tag, boolean recolorManualTags) {
        if (tag == null) {
            return false;
        }
        String color = tag.getColor();
        if (color == null || color.isBlank()) {
            return true;
        }
        if (AI_DEFAULT_PURPLE.equalsIgnoreCase(color)) {
            return true;
        }
        return recolorManualTags || !"MANUAL".equalsIgnoreCase(tag.getSource());
    }
}
