package com.mediamanager.metadata.controller;

import com.mediamanager.common.response.ApiResponse;
import com.mediamanager.metadata.dto.ScrapeTaskResponse;
import com.mediamanager.metadata.service.ScrapeTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for managing metadata scraping tasks.
 *
 * Endpoints:
 *   POST /api/scrape/start              — trigger scrape for all libraries
 *   POST /api/scrape/start/{libraryId}  — trigger scrape for specific library
 *   GET  /api/scrape/tasks              — list all tasks
 *   GET  /api/scrape/tasks/{id}         — get task detail
 *   POST /api/scrape/tasks/{id}/cancel  — cancel running task
 */
@RestController
@RequestMapping("/api/v1/scrape")
@RequiredArgsConstructor
public class ScrapeTaskController {

    private final ScrapeTaskService scrapeTaskService;

    /**
     * Start a scrape across all libraries.
     *
     * @param body optional JSON body with:
     *   - targetStatus: "UNIDENTIFIED" (default) | "IDENTIFIED" | "ALL"
     */
    /**
     * Document-aligned alias for creating a scrape task (all libraries).
     */
    @PostMapping("/tasks")
    @PreAuthorize("hasAuthority('library:edit') or hasAuthority('task:execute')")
    public ApiResponse<ScrapeTaskResponse> createTask(@RequestBody(required = false) Map<String, Object> body) {
        String targetStatus = "UNIDENTIFIED";
        Integer libraryId = null;
        if (body != null) {
            if (body.get("targetStatus") != null) {
                targetStatus = String.valueOf(body.get("targetStatus"));
            }
            if (body.get("libraryId") != null) {
                libraryId = Integer.parseInt(String.valueOf(body.get("libraryId")));
            }
        }
        ScrapeTaskResponse task = scrapeTaskService.startScrape(libraryId, "MANUAL", targetStatus);
        return ApiResponse.success(task);
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<ScrapeTaskResponse> startScrapeAll(@RequestBody(required = false) Map<String, String> body) {
        String targetStatus = body != null ? body.getOrDefault("targetStatus", "UNIDENTIFIED") : "UNIDENTIFIED";
        ScrapeTaskResponse task = scrapeTaskService.startScrape(null, "MANUAL", targetStatus);
        if (task == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(task);
    }

    /**
     * Start a scrape for a specific library.
     *
     * @param libraryId library to scrape
     * @param body optional JSON body with:
     *   - targetStatus: "UNIDENTIFIED" (default) | "IDENTIFIED" | "ALL"
     */
    @PostMapping("/start/{libraryId}")
    @PreAuthorize("hasAuthority('library:edit')")
    public ApiResponse<ScrapeTaskResponse> startScrapeLibrary(
            @PathVariable Integer libraryId,
            @RequestBody(required = false) Map<String, String> body) {
        String targetStatus = body != null ? body.getOrDefault("targetStatus", "UNIDENTIFIED") : "UNIDENTIFIED";
        ScrapeTaskResponse task = scrapeTaskService.startScrape(libraryId, "MANUAL", targetStatus);
        if (task == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(task);
    }

    /**
     * List all scrape tasks (newest first).
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<List<ScrapeTaskResponse>> listTasks(@RequestParam(required = false) Integer scheduleId) {
        if (scheduleId == null) {
            return ApiResponse.success(scrapeTaskService.getAllTasks());
        }
        return ApiResponse.success(scrapeTaskService.getTasksByScheduleId(scheduleId.longValue()));
    }

    /**
     * Get a specific task's details.
     */
    @GetMapping("/tasks/{id}")
    @PreAuthorize("hasAuthority('task:view')")
    public ApiResponse<ScrapeTaskResponse> getTask(@PathVariable Integer id) {
        ScrapeTaskResponse task = scrapeTaskService.getTask(id);
        if (task == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(task);
    }

    /**
     * Cancel a running or pending task.
     */
    @PostMapping("/tasks/{id}/cancel")
    @PreAuthorize("hasAuthority('library:edit') or hasAuthority('task:execute')")
    public ApiResponse<Boolean> cancelTask(@PathVariable Integer id) {
        boolean cancelled = scrapeTaskService.cancelTask(id);
        return ApiResponse.success(cancelled);
    }
}
