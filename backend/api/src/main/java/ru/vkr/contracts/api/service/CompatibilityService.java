package ru.vkr.contracts.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.CompatibilityReport;
import ru.vkr.contracts.api.repo.CompatibilityReportRepository;

import java.util.List;

@Service
public class CompatibilityService {
    private final CompatibilityReportRepository repository;

    public CompatibilityService(CompatibilityReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CompatibilityReport> list() {
        return repository.findAll();
    }
}
