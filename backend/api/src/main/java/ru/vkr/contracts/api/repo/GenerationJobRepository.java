package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.shared.model.JobStatus;

import java.time.Instant;
import java.util.List;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {
    java.util.Optional<GenerationJob> findTopByCorrelationIdOrderByIdDesc(String correlationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GenerationJob j
            set j.status = :nextStatus, j.updatedAt = :updatedAt
            where j.id = :jobId and j.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("jobId") Long jobId,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("nextStatus") JobStatus nextStatus,
            @Param("updatedAt") Instant updatedAt
    );

    List<GenerationJob> findByStatusAndUpdatedAtBefore(JobStatus status, Instant updatedAtBefore);
    List<GenerationJob> findByStatusAndCreatedAtBefore(JobStatus status, Instant createdAtBefore);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GenerationJob j
            set j.status = :status, j.updatedAt = :updatedAt
            where j.id = :jobId
            """)
    int updateStatusAndUpdatedAt(
            @Param("jobId") Long jobId,
            @Param("status") JobStatus status,
            @Param("updatedAt") Instant updatedAt
    );
}
