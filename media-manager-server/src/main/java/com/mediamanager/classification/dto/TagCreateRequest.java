package com.mediamanager.classification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagCreateRequest {
    @NotBlank
    private String name;
    private String color;
}
