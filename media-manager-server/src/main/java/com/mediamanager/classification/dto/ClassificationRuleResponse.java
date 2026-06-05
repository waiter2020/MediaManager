package com.mediamanager.classification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationRuleResponse {
    private Integer id;
    private String name;
    private String ruleType;
    private String expression;
    private String targetType;
    private String targetValue;
    private Boolean enabled;
    private Integer priority;
    private Instant createdAt;
}
