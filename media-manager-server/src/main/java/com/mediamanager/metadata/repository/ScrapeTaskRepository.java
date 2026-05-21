package com.mediamanager.metadata.repository;

import com.mediamanager.metadata.entity.ScrapeTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScrapeTaskRepository extends JpaRepository<ScrapeTask, Integer> {

    List<ScrapeTask> findByStatusIn(List<String> statuses);

    List<ScrapeTask> findByStatusOrderByCreatedAtDesc(String status);

    List<ScrapeTask> findAllByOrderByCreatedAtDesc();

    boolean existsByStatusIn(List<String> statuses);

    List<ScrapeTask> findByScheduleIdOrderByCreatedAtDesc(Long scheduleId);
}
