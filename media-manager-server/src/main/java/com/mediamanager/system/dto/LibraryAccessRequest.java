package com.mediamanager.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class LibraryAccessRequest {
    private List<LibraryAccessItem> items;

    @Data
    public static class LibraryAccessItem {
        private Integer libraryId;
        private Boolean canView = true;
        private Boolean canEdit = false;
        private Boolean canDeleteFile = false;
    }
}
