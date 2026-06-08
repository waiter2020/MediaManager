package com.mediamanager.media.dto;

import lombok.Data;

import java.util.List;

@Data
public class MediaCollectionRuleDto {
    private Integer libraryId;
    private String type;
    private String keyword;
    private List<Integer> categoryIds;
    private List<Integer> tagIds;
    private String metadataField;
    private String metadataValue;
    private Integer minYear;
    private Integer maxYear;
    private Double minRating;
    private String sortField;
    private String sortOrder;
    private Integer limit;
    private Boolean unwatchedOnly;
}
