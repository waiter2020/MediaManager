package com.mediamanager.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400xx - Validation
    VALIDATION_ERROR(40001, "Validation failed"),
    INVALID_PARAMETER(40002, "Invalid parameter"),

    // 401xx - Authentication
    TOKEN_EXPIRED(40101, "Token expired"),
    INVALID_TOKEN(40102, "Invalid token"),
    INVALID_CREDENTIALS(40103, "Invalid username or password"),
    ACCOUNT_DISABLED(40104, "Account is disabled"),

    // 403xx - Authorization
    ACCESS_DENIED(40301, "Access denied"),
    LIBRARY_ACCESS_DENIED(40302, "Library access denied"),

    // 404xx - Not Found
    USER_NOT_FOUND(40401, "User not found"),
    LIBRARY_NOT_FOUND(40402, "Library not found"),
    MEDIA_ITEM_NOT_FOUND(40403, "Media item not found"),
    MEDIA_FILE_NOT_FOUND(40404, "Media file not found"),
    TAG_NOT_FOUND(40405, "Tag not found"),
    CATEGORY_NOT_FOUND(40406, "Category not found"),

    // 409xx - Conflict
    USERNAME_EXISTS(40901, "Username already exists"),
    TAG_NAME_EXISTS(40902, "Tag name already exists"),

    // 500xx - Server Error
    INTERNAL_ERROR(50001, "Internal server error"),
    FFMPEG_ERROR(50002, "FFmpeg processing error"),
    METADATA_EXTRACT_ERROR(50003, "Metadata extraction error"),
    FILE_SYSTEM_ERROR(50004, "File system error"),

    // Setup
    SETUP_ALREADY_COMPLETED(40010, "Setup has already been completed");

    private final int code;
    private final String message;
}
