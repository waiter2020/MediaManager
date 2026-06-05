package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class MediaCollectionResponse {
    private Integer id;
    private Integer ownerUserId;
    private String ownerDisplayName;
    private String name;
    private String description;
    private String type;
    private String visibility;
    private String posterPath;
    private Boolean smart;
    private MediaCollectionRuleDto rule;
    private MediaItemResponse coverItem;
    private Integer itemCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MediaItemResponse> items;
}
