package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeneralSettingsDto {
    private String version;
    private boolean setupCompleted;
}
