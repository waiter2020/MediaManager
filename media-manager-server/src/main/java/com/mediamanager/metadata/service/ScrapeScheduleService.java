package com.mediamanager.metadata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.metadata.entity.ScrapeSchedule;
import com.mediamanager.metadata.repository.ScrapeScheduleRepository;
import com.mediamanager.common.exception.BusinessException;
import com.mediamanager.common.exception.ErrorCode;
import com.mediamanager.common.security.SecurityCurrentUser;
import com.mediamanager.plugin.PluginKind;
import com.mediamanager.plugin.repository.LibraryPluginConfigRepository;
import com.mediamanager.system.service.LibraryAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeScheduleService {

    private final ScrapeScheduleRepository scheduleRepository;
    private final MediaLibraryRepository libraryRepository;
    private final ObjectMapper objectMapper;
    private final LibraryAccessService libraryAccessService;
    private final SecurityCurrentUser securityCurrentUser;
    private final LibraryPluginConfigRepository pluginConfigRepository;

    @Transactional(readOnly = true)
    public List<ScrapeSchedule> listSchedules() {
        return scheduleRepository.findAll().stream()
                .filter(this::canViewSchedule)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScrapeSchedule getSchedule(Integer id) {
        return scheduleRepository.findById(id)
                .filter(this::canViewSchedule)
                .orElse(null);
    }

    @Transactional
    public ScrapeSchedule createSchedule(ScrapeSchedule schedule) {
        assertCanManageSchedule(schedule);
        validateAndNormalize(schedule);
        if (schedule.getNextRunAt() == null) {
            schedule.setNextRunAt(computeNextRunAt(schedule, Instant.now()));
        }
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public ScrapeSchedule updateSchedule(Integer id, ScrapeSchedule updates) {
        return scheduleRepository.findById(id).map(existing -> {
            assertCanManageSchedule(existing);
            assertCanManageSchedule(updates);
            existing.setName(updates.getName());
            existing.setEnabled(updates.getEnabled());
            existing.setScheduleType(updates.getScheduleType());
            existing.setCronExpr(updates.getCronExpr());
            existing.setIntervalSeconds(updates.getIntervalSeconds());
            existing.setScope(updates.getScope());
            existing.setLibrary(updates.getLibrary());
            existing.setTargetStatus(updates.getTargetStatus());
            existing.setMediaTypes(updates.getMediaTypes());
            existing.setMaxConcurrency(updates.getMaxConcurrency());
            existing.setBatchSizeOverride(updates.getBatchSizeOverride());
            existing.setRequestDelayMsOverride(updates.getRequestDelayMsOverride());

            validateAndNormalize(existing);

            // Recompute next run time on updates.
            existing.setNextRunAt(computeNextRunAt(existing, Instant.now()));
            return scheduleRepository.save(existing);
        }).orElse(null);
    }

    @Transactional
    public boolean deleteSchedule(Integer id) {
        ScrapeSchedule existing = scheduleRepository.findById(id).orElse(null);
        if (existing == null) {
            return false;
        }
        assertCanManageSchedule(existing);
        scheduleRepository.deleteById(id);
        return true;
    }

    private boolean canViewSchedule(ScrapeSchedule schedule) {
        if ("GLOBAL".equalsIgnoreCase(schedule.getScope())) {
            return libraryAccessService.bypassesLibraryRestrictions(
                    securityCurrentUser.getCurrentUser().orElse(null));
        }
        if (schedule.getLibrary() == null || schedule.getLibrary().getId() == null) {
            return false;
        }
        Set<Integer> allowed = libraryAccessService.getViewableLibraryIds(securityCurrentUser.getCurrentUser());
        return allowed.contains(schedule.getLibrary().getId());
    }

    private void assertCanManageSchedule(ScrapeSchedule schedule) {
        if ("GLOBAL".equalsIgnoreCase(schedule.getScope())) {
            if (!libraryAccessService.bypassesLibraryRestrictions(
                    securityCurrentUser.getCurrentUser().orElse(null))) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED,
                        "GLOBAL scrape schedules require admin privileges");
            }
            return;
        }
        if (schedule.getLibrary() != null && schedule.getLibrary().getId() != null) {
            libraryAccessService.assertCanEditLibrary(schedule.getLibrary().getId());
        }
    }

    public Instant computeNextRunAt(ScrapeSchedule schedule, Instant from) {
        if (schedule == null) return null;
        String type = schedule.getScheduleType();
        if ("CRON".equalsIgnoreCase(type)) {
            String expr = schedule.getCronExpr();
            if (expr == null || expr.isBlank()) return null;
            CronExpression cron = CronExpression.parse(expr);
            ZonedDateTime next = cron.next(ZonedDateTime.ofInstant(from, ZoneId.systemDefault()));
            return next != null ? next.toInstant() : null;
        }
        if ("FIXED_DELAY".equalsIgnoreCase(type)) {
            Integer seconds = schedule.getIntervalSeconds();
            if (seconds == null || seconds <= 0) return null;
            return from.plusSeconds(seconds);
        }
        return null;
    }

    private void validateAndNormalize(ScrapeSchedule schedule) {
        if (schedule.getEnabled() == null) {
            schedule.setEnabled(true);
        }
        if (schedule.getMaxConcurrency() == null || schedule.getMaxConcurrency() <= 0) {
            schedule.setMaxConcurrency(1);
        }

        String scope = schedule.getScope() != null ? schedule.getScope().toUpperCase() : "GLOBAL";
        schedule.setScope(scope);

        if ("LIBRARY".equals(scope)) {
            if (schedule.getLibrary() == null || schedule.getLibrary().getId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "libraryId is required when scope is LIBRARY");
            }
            MediaLibrary lib = libraryRepository.findById(schedule.getLibrary().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.LIBRARY_NOT_FOUND));
            schedule.setLibrary(lib);
            assertLibraryHasEnabledScraper(lib.getId());
        } else {
            schedule.setLibrary(null);
        }

        String scheduleType = schedule.getScheduleType() != null
                ? schedule.getScheduleType().toUpperCase()
                : null;
        schedule.setScheduleType(scheduleType);

        if ("CRON".equals(scheduleType)) {
            if (schedule.getCronExpr() == null || schedule.getCronExpr().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "cronExpr is required for CRON schedules");
            }
            try {
                CronExpression.parse(schedule.getCronExpr());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid cron expression: " + e.getMessage());
            }
            schedule.setIntervalSeconds(null);
        } else if ("FIXED_DELAY".equals(scheduleType)) {
            if (schedule.getIntervalSeconds() == null || schedule.getIntervalSeconds() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "intervalSeconds must be > 0 for FIXED_DELAY schedules");
            }
            schedule.setCronExpr(null);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "scheduleType must be CRON or FIXED_DELAY");
        }

        if (schedule.getMediaTypes() != null && !schedule.getMediaTypes().isBlank()) {
            try {
                objectMapper.readValue(schedule.getMediaTypes(), List.class);
            } catch (JsonProcessingException e) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "mediaTypes must be a JSON array string");
            }
        } else {
            schedule.setMediaTypes(null);
        }
    }

    /**
     * Warn-level guard: schedules targeting a library should have at least one enabled SCRAPER plugin.
     */
    private void assertLibraryHasEnabledScraper(Integer libraryId) {
        boolean hasScraper = pluginConfigRepository.findByLibrary_IdOrderByPriorityAsc(libraryId).stream()
                .anyMatch(c -> PluginKind.SCRAPER.name().equals(c.getKind()) && Boolean.TRUE.equals(c.getEnabled()));
        if (!hasScraper) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Library has no enabled SCRAPER plugin (e.g. tmdb); configure plugins before creating a library schedule");
        }
    }

    @Transactional(readOnly = true)
    public List<String> listEnabledScraperIds(Integer libraryId) {
        libraryAccessService.assertCanViewLibrary(libraryId);
        return pluginConfigRepository.findByLibrary_IdOrderByPriorityAsc(libraryId).stream()
                .filter(c -> PluginKind.SCRAPER.name().equals(c.getKind()) && Boolean.TRUE.equals(c.getEnabled()))
                .map(c -> c.getPluginId())
                .toList();
    }

    public String buildTaskParamsJson(ScrapeSchedule schedule) {
        if (schedule == null) return null;
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            if (schedule.getBatchSizeOverride() != null) {
                params.put("batchSize", schedule.getBatchSizeOverride());
            }
            if (schedule.getRequestDelayMsOverride() != null) {
                params.put("requestDelayMs", schedule.getRequestDelayMsOverride());
            }
            if (params.isEmpty()) return null;
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize task params", e);
            return null;
        }
    }
}

