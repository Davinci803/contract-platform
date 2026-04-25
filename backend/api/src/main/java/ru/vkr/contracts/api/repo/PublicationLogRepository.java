package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.PublicationLog;

public interface PublicationLogRepository extends JpaRepository<PublicationLog, Long> {
    long countByJob_IdAndStatus(Long jobId, String status);
    long countByJob_IdAndTargetAndStatus(Long jobId, String target, String status);
}
