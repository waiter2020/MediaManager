package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubtitleProviderStatusDto {
    private String id;
    private boolean configured;
    private boolean enabled;
}
