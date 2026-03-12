package com.mediamanager.system.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String displayName;
    private String email;
    private Boolean enabled;
}
