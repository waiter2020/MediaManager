package com.mediamanager.metadata.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.metadata.dto.ScrapeScheduleDto;
import com.mediamanager.metadata.dto.ScrapeTaskResponse;
import com.mediamanager.metadata.entity.ScrapeSchedule;
import com.mediamanager.metadata.service.ScrapeScheduleService;
import com.mediamanager.metadata.service.ScrapeTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/scrape/schedules")
@RequiredArgsConstructor
public class ScrapeScheduleController {

    private final ScrapeScheduleService scheduleService;
    private final ScrapeTaskService taskService;

    @GetMapping
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<List<ScrapeScheduleDto>> list() {
        List<ScrapeScheduleDto> dtos = scheduleService.listSchedules().stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.success(dtos);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<ScrapeScheduleDto> get(@PathVariable Integer id) {
        ScrapeSchedule schedule = scheduleService.getSchedule(id);
        return ApiResponse.success(schedule != null ? toDto(schedule) : null);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<ScrapeScheduleDto> create(@Valid @RequestBody ScrapeScheduleDto body) {
        ScrapeSchedule created = scheduleService.createSchedule(fromDto(body));
        return ApiResponse.success(toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<ScrapeScheduleDto> update(@PathVariable Integer id, @Valid @RequestBody ScrapeScheduleDto body) {
        ScrapeSchedule updated = scheduleService.updateSchedule(id, fromDto(body));
        return ApiResponse.success(updated != null ? toDto(updated) : null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<Boolean> delete(@PathVariable Integer id) {
        return ApiResponse.success(scheduleService.deleteSchedule(id));
    }

    /**
     * Trigger a schedule once immediately (creates a scrape_task).
     */
    @PostMapping("/{id}/runOnce")
    @PreAuthorize("hasAuthority('task:execute') or hasAuthority('library:edit')")
    public ApiResponse<ScrapeTaskResponse> runOnce(@PathVariable Integer id) {
        ScrapeSchedule schedule = scheduleService.getSchedule(id);
        if (schedule == null) return ApiResponse.success(null);

        Integer libraryId = schedule.getLibrary() != null ? schedule.getLibrary().getId() : null;
        String paramsJson = scheduleService.buildTaskParamsJson(schedule);
        ScrapeTaskResponse task = taskService.startScrapeWithParams(
                schedule.getId().longValue(),
                libraryId,
                "MANUAL",
                schedule.getTargetStatus(),
                schedule.getMediaTypes(),
                paramsJson
        );

        // Optional: push next_run_at forward if schedule is enabled and due now
        if (Boolean.TRUE.equals(schedule.getEnabled())
                && schedule.getNextRunAt() != null
                && !schedule.getNextRunAt().isAfter(Instant.now())) {
            schedule.setNextRunAt(scheduleService.computeNextRunAt(schedule, Instant.now()));
            scheduleService.updateSchedule(id, schedule);
        }

        return ApiResponse.success(task);
    }

    private ScrapeScheduleDto toDto(ScrapeSchedule s) {
        return ScrapeScheduleDto.builder()
                .id(s.getId())
                .name(s.getName())
                .enabled(s.getEnabled())
                .scheduleType(s.getScheduleType())
                .cronExpr(s.getCronExpr())
                .intervalSeconds(s.getIntervalSeconds())
                .scope(s.getScope())
                .libraryId(s.getLibrary() != null ? s.getLibrary().getId() : null)
                .targetStatus(s.getTargetStatus())
                .mediaTypes(s.getMediaTypes())
                .maxConcurrency(s.getMaxConcurrency())
                .batchSizeOverride(s.getBatchSizeOverride())
                .requestDelayMsOverride(s.getRequestDelayMsOverride())
                .nextRunAt(s.getNextRunAt())
                .lastRunAt(s.getLastRunAt())
                .lastStatus(s.getLastStatus())
                .lastError(s.getLastError())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private ScrapeSchedule fromDto(ScrapeScheduleDto d) {
        MediaLibrary lib = null;
        if ("LIBRARY".equalsIgnoreCase(d.getScope()) && d.getLibraryId() != null) {
            lib = MediaLibrary.builder().id(d.getLibraryId()).build();
        }
        return ScrapeSchedule.builder()
                .name(d.getName())
                .enabled(d.getEnabled())
                .scheduleType(d.getScheduleType())
                .cronExpr(d.getCronExpr())
                .intervalSeconds(d.getIntervalSeconds())
                .scope(d.getScope())
                .library(lib)
                .targetStatus(d.getTargetStatus())
                .mediaTypes(d.getMediaTypes())
                .maxConcurrency(d.getMaxConcurrency())
                .batchSizeOverride(d.getBatchSizeOverride())
                .requestDelayMsOverride(d.getRequestDelayMsOverride())
                .build();
    }
}

