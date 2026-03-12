package com.mediamanager.classification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryCreateRequest {
    @NotBlank
    private String name;
    private Integer parentId;
    private String type = "CUSTOM";
}
