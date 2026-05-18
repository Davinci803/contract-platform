package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.PublicationLog;

import java.util.List;

public interface PublicationLogRepository extends JpaRepository<PublicationLog, Long> {
    long countByJob_IdAndStatus(Long jobId, String status);
    long countByJob_IdAndTargetAndStatus(Long jobId, String target, String status);
    List<PublicationLog> findByJob_IdOrderByCreatedAtAsc(Long jobId);
    List<PublicationLog> findTop50ByOrderByIdDesc();
    List<PublicationLog> findTop50ByCorrelationIdOrderByIdDesc(String correlationId);
}
