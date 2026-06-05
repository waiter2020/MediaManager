package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.ScrapeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ScrapeScheduleRepository extends JpaRepository<ScrapeSchedule, Integer> {
    List<ScrapeSchedule> findByEnabledTrueAndNextRunAtLessThanEqual(Instant now);
}

