package com.mediamanager.classification.dto;

import lombok.Data;

@Data
public class CategoryUpdateRequest {
    private String name;
    private Integer parentId;
    private String type;
}
