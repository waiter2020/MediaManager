package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntegrationsSettingsDto {
    /** Masked as *** when a key is configured. */
    private String tmdbApiKey;
    private boolean tmdbApiKeyConfigured;
}
