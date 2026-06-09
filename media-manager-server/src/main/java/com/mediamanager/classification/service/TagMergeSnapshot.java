package com.mediamanager.classification.service;

public record TagMergeSnapshot(
        Integer id,
        String name,
        Long usageCount,
        String source) {

    public static TagMergeSnapshot of(Integer id, String name) {
        return new TagMergeSnapshot(id, name, 0L, null);
    }
}
