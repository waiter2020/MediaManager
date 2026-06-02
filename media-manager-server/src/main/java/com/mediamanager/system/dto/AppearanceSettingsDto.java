package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppearanceSettingsDto {
    /** dark | light | system */
    private String theme;
}
