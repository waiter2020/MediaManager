package com.mediamanager.system.dto;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class MediaProcessingSettingsUpdateRequest {
    @NotBlank(message = "FFmpeg path is required")
    @Size(max = 255, message = "FFmpeg path cannot exceed 255 characters")
    private String ffmpegPath;

    @NotBlank(message = "FFprobe path is required")
    @Size(max = 255, message = "FFprobe path cannot exceed 255 characters")
    private String ffprobePath;
}
