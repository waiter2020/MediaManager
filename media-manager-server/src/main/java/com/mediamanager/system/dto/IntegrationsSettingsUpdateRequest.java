package com.mediamanager.system.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class IntegrationsSettingsUpdateRequest {
    @Size(max = 255, message = "TMDb API Key cannot exceed 255 characters")
    private String tmdbApiKey;
    @Size(max = 255, message = "OpenSubtitles API Key cannot exceed 255 characters")
    private String opensubtitlesApiKey;
    @Size(max = 255, message = "OpenSubtitles username cannot exceed 255 characters")
    private String opensubtitlesUsername;
    @Size(max = 255, message = "OpenSubtitles password cannot exceed 255 characters")
    private String opensubtitlesPassword;
    @Size(max = 32, message = "Default subtitle language cannot exceed 32 characters")
    private String subtitleDefaultLanguage;
}
