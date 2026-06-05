package com.mediamanager.system.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SysConfigServiceOllamaUrlTest {

    @Test
    void recognizesSeedLocalhostUrls() {
        assertTrue(SysConfigService.isSeedLocalhostOllamaUrl("http://localhost:11434"));
        assertTrue(SysConfigService.isSeedLocalhostOllamaUrl("http://localhost:11434/"));
        assertTrue(SysConfigService.isSeedLocalhostOllamaUrl("http://127.0.0.1:11434"));
    }

    @Test
    void ignoresDockerHostUrls() {
        assertFalse(SysConfigService.isSeedLocalhostOllamaUrl("http://host.docker.internal:11434"));
        assertFalse(SysConfigService.isSeedLocalhostOllamaUrl(""));
        assertFalse(SysConfigService.isSeedLocalhostOllamaUrl(null));
    }
}
