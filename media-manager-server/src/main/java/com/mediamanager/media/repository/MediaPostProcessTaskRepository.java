package com.mediamanager.media.repository;

import com.mediamanager.media.entity.MediaPostProcessTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MediaPostProcessTaskRepository extends JpaRepository<MediaPostProcessTask, Integer> {

    long countByStatus(String status);

    boolean existsByTaskTypeAndMediaItemIdAndStatusIn(
            String taskType, Integer mediaItemId, List<String> statuses);

    boolean existsByTaskTypeAndMediaFileIdAndStatusIn(
            String taskType, Integer mediaFileId, List<String> statuses);

    @Query(value = """
            SELECT id FROM media_post_process_task
            WHERE status = 'PENDING' AND next_retry_at <= CURRENT_TIMESTAMP
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Integer> findClaimableTaskIds(@Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MediaPostProcessTask t
            SET t.status = 'PENDING', t.startedAt = NULL
            WHERE t.status = 'RUNNING' AND t.startedAt < :staleBefore
            """)
    int resetStaleRunningTasks(@Param("staleBefore") Instant staleBefore);
}
