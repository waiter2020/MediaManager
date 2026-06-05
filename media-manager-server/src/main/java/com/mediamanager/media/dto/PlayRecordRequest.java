package com.mediamanager.media.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Typed DTO replacing raw Map for playback recording requests.
 */
public record PlayRecordRequest(
        @NotNull Long mediaItemId,
        Integer progressSeconds,
        Integer durationSeconds
) {
}
