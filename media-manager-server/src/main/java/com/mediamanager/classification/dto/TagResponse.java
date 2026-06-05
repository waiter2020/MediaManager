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
public class TagResponse {
    private Integer id;
    private String name;
    private String color;
    private String source;
    private Long usageCount;
    private Instant createdAt;
}
