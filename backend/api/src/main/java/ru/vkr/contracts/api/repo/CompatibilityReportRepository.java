package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.CompatibilityReport;

public interface CompatibilityReportRepository extends JpaRepository<CompatibilityReport, Long> {
}
