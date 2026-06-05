package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SecuritySettingsDto {
    /** Saved auth setting. It becomes effective after application restart. */
    private boolean authEnabled;
    /** Auth state currently used by the running SecurityFilterChain. */
    private boolean effectiveAuthEnabled;
    /** Changing auth requires application restart for SecurityFilterChain to take effect. */
    private boolean requiresRestart;
    /** Saved setting differs from the running SecurityFilterChain state. */
    private boolean restartRequired;
}
