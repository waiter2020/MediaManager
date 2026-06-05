package com.mediamanager.media.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MediaCollectionItemsRequest {

    @NotEmpty
    private List<Integer> itemIds;
}
