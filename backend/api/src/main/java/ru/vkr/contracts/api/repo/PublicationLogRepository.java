package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.PublicationLog;

public interface PublicationLogRepository extends JpaRepository<PublicationLog, Long> {
}
