package com.mediamanager.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoleAssignRequest {
    private List<String> roleCodes;
}
