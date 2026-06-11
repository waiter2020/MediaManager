package com.mediamanager.media.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DeleteBatchRequest {

    @NotEmpty
    @Size(max = 100)
    private List<Integer> itemIds;

    private boolean deleteSourceFile = false;
}
