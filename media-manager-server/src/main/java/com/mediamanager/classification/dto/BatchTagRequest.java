package com.mediamanager.classification.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchTagRequest {
    private List<Integer> mediaItemIds;
    private List<Integer> tagIds;
}
