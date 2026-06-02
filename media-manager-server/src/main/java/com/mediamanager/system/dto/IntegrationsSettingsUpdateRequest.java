package com.mediamanager.system.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class IntegrationsSettingsUpdateRequest {
    @Size(max = 255, message = "TMDb API Key cannot exceed 255 characters")
    private String tmdbApiKey;
}
