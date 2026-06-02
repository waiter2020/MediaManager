package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaProcessingSettingsDto {
    private String ffmpegPath;
    private String ffprobePath;
}
