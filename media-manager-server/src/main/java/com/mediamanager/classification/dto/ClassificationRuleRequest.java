package com.mediamanager.classification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClassificationRuleRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String ruleType;
    @NotBlank
    private String expression;
    @NotBlank
    private String targetType;
    @NotBlank
    private String targetValue;
    private Boolean enabled = true;
    private Integer priority = 0;
}
