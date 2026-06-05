package com.mediamanager.media.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClassifyBatchRequest {

    @NotEmpty
    @Size(max = 100)
    private List<Integer> itemIds;
}
