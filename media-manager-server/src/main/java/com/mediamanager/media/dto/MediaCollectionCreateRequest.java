package com.mediamanager.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MediaCollectionCreateRequest {

    @NotBlank
    @Size(max = 128)
    private String name;

    private String description;

    private String type;

    private String visibility;

    private Boolean smart;

    private MediaCollectionRuleDto rule;

    private List<Integer> itemIds;
}
