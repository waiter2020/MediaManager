package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubtitleSettingsDto {
    private String defaultLanguage;
    private List<SubtitleProviderStatusDto> providers;
}
