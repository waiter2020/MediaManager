package com.mediamanager.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SystemLogEventDto {

    private long timestamp;
    private String level;
    private String source; // APP / TASK / OTHER
    private String logger;
    private String message;
    private String thread;
    private String exceptionShort;
    private String type;
    private Integer libraryId;
    private String summary;
}

