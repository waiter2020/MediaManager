package com.mediamanager.common.security;

import com.mediamanager.system.entity.SysUser;
import com.mediamanager.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SecurityCurrentUser {

    private final SysConfigService sysConfigService;

    @Value("${mediamanager.auth.enabled:true}")
    private boolean yamlAuthEnabled;

    public Optional<SysUser> getCurrentUser() {
        if (!sysConfigService.isEffectiveAuthEnabled(yamlAuthEnabled)) {
            return Optional.empty();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof SysUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public SysUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Not authenticated"));
    }
}
