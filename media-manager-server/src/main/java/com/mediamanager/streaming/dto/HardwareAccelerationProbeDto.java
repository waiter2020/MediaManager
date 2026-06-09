package com.mediamanager.streaming.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record HardwareAccelerationProbeDto(
        String configuredType,
        String resolvedType,
        String resolvedEncoder,
        String devicePath,
        Map<String, Boolean> encodersAvailable,
        List<String> warnings) {
}
