package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntegrationsSettingsDto {
    /** Masked as *** when a key is configured. */
    private String tmdbApiKey;
    private boolean tmdbApiKeyConfigured;
    private String opensubtitlesApiKey;
    private boolean opensubtitlesApiKeyConfigured;
    private String opensubtitlesUsername;
    private boolean opensubtitlesUsernameConfigured;
    private String opensubtitlesPassword;
    private boolean opensubtitlesPasswordConfigured;
    private String subtitleDefaultLanguage;
}
