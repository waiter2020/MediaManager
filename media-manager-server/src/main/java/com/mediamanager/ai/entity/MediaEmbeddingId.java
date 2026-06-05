package com.mediamanager.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaEmbeddingId implements Serializable {
    private Integer mediaItemId;
    private String modelId;
}
