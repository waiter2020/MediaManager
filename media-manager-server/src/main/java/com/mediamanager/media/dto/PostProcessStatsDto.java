package com.mediamanager.media.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PostProcessStatsDto {
    long pending;
    long running;
    long failed;
    long success;
}
