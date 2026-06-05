package com.mediamanager.media.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaCollectionUpdateRequest {

    @Size(max = 128)
    private String name;

    private String description;

    private String visibility;

    private Boolean smart;

    private MediaCollectionRuleDto rule;
}
