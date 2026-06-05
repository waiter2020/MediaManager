package com.mediamanager.system.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class AppearanceSettingsUpdateRequest {
    @NotBlank(message = "Theme is required")
    @Size(max = 64, message = "Theme name cannot exceed 64 characters")
    private String theme;
}
