package com.mediamanager.system.dto;

import com.mediamanager.streaming.dto.HardwareAccelerationProbeDto;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaProcessingSettingsDto {
    private String ffmpegPath;
    private String ffprobePath;
    private String hardwareAcceleration;
    private String hardwareDevice;
    private String hardwareEncoder;
    private HardwareAccelerationProbeDto hardwareProbe;
}
