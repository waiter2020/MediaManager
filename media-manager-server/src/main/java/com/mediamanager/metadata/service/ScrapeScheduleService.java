package com.mediamanager.metadata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediamanager.library.entity.MediaLibrary;
import com.mediamanager.library.repository.MediaLibraryRepository;
import com.mediamanager.metadata.entity.ScrapeSchedule;
import com.mediamanager.metadata.repository.ScrapeScheduleRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeScheduleService {

    private final ScrapeScheduleRepository scheduleRepository;
    private final MediaLibraryRepository libraryRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ScrapeSchedule> listSchedules() {
        return scheduleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ScrapeSchedule getSchedule(Integer id) {
        return scheduleRepository.findById(id).orElse(null);
    }

    @Transactional
    public ScrapeSchedule createSchedule(ScrapeSchedule schedule) {
        validateAndNormalize(schedule);
        if (schedule.getNextRunAt() == null) {
            schedule.setNextRunAt(computeNextRunAt(schedule, Instant.now()));
        }
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public ScrapeSchedule updateSchedule(Integer id, ScrapeSchedule updates) {
        return scheduleRepository.findById(id).map(existing -> {
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
        if (!scheduleRepository.existsById(id)) return false;
        scheduleRepository.deleteById(id);
        return true;
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
        if (schedule.getName() == null || schedule.getName().isBlank()) {
            schedule.setName("ScrapeSchedule");
        }
        if (schedule.getEnabled() == null) schedule.setEnabled(true);
        if (schedule.getScope() == null || schedule.getScope().isBlank()) schedule.setScope("GLOBAL");
        if (schedule.getTargetStatus() == null || schedule.getTargetStatus().isBlank()) schedule.setTargetStatus("UNIDENTIFIED");
        if (schedule.getMaxConcurrency() == null || schedule.getMaxConcurrency() <= 0) schedule.setMaxConcurrency(1);

        // Resolve library if present (we accept an entity with only id set from controller).
        if (schedule.getLibrary() != null && schedule.getLibrary().getId() != null) {
            MediaLibrary lib = libraryRepository.findById(schedule.getLibrary().getId()).orElse(null);
            schedule.setLibrary(lib);
        }

        // Validate schedule_type payload.
        if ("CRON".equalsIgnoreCase(schedule.getScheduleType())) {
            CronExpression.parse(schedule.getCronExpr());
            schedule.setIntervalSeconds(null);
        } else if ("FIXED_DELAY".equalsIgnoreCase(schedule.getScheduleType())) {
            if (schedule.getIntervalSeconds() == null || schedule.getIntervalSeconds() <= 0) {
                throw new IllegalArgumentException("intervalSeconds must be > 0 for FIXED_DELAY");
            }
            schedule.setCronExpr(null);
        } else {
            throw new IllegalArgumentException("scheduleType must be CRON or FIXED_DELAY");
        }

        // Validate mediaTypes JSON if provided.
        if (schedule.getMediaTypes() != null && !schedule.getMediaTypes().isBlank()) {
            try {
                objectMapper.readValue(schedule.getMediaTypes(), List.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("mediaTypes must be a JSON array string", e);
            }
        }
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

