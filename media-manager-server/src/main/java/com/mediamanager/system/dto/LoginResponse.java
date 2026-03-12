package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Integer id;
        private String username;
        private String displayName;
        private String avatarPath;
        private Set<String> roles;
        private Set<String> permissions;
    }
}
