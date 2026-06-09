package com.mediamanager.media.service;

import com.mediamanager.media.spi.SubtitleSearchProvider;
import com.mediamanager.system.dto.SubtitleProviderStatusDto;
import com.mediamanager.system.dto.SubtitleSettingsDto;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubtitleSettingsService {

    private final SysConfigService sysConfigService;
    private final List<SubtitleSearchProvider> searchProviders;

    public SubtitleSettingsDto getSubtitleSettings() {
        List<SubtitleProviderStatusDto> providers = searchProviders.stream()
                .map(provider -> SubtitleProviderStatusDto.builder()
                        .id(provider.id())
                        .configured(provider.isConfigured())
                        .enabled(provider.isConfigured())
                        .build())
                .toList();
        return SubtitleSettingsDto.builder()
                .defaultLanguage(sysConfigService.subtitleDefaultLanguage())
                .providers(providers)
                .build();
    }
}
