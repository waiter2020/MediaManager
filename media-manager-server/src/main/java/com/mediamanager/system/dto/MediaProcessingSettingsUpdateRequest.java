package com.mediamanager.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MediaProcessingSettingsUpdateRequest {
    @Size(max = 255, message = "FFmpeg path cannot exceed 255 characters")
    private String ffmpegPath;

    @Size(max = 255, message = "FFprobe path cannot exceed 255 characters")
    private String ffprobePath;

    @Size(max = 32, message = "Hardware acceleration type cannot exceed 32 characters")
    private String hardwareAcceleration;

    @Size(max = 255, message = "Hardware device path cannot exceed 255 characters")
    private String hardwareDevice;

    @Size(max = 64, message = "Hardware encoder cannot exceed 64 characters")
    private String hardwareEncoder;
}
