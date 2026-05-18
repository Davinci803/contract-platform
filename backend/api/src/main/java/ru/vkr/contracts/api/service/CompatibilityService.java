package ru.vkr.contracts.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.CompatibilityReport;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.repo.CompatibilityReportRepository;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.worker.compat.ChangeSeverity;
import ru.vkr.contracts.worker.compat.CompatibilityAnalyzer;
import ru.vkr.contracts.worker.compat.CompatibilityFinding;
import ru.vkr.contracts.worker.compat.CompatibilityResult;

import java.util.List;

@Service
public class CompatibilityService {
    private final CompatibilityReportRepository repository;
    private final ContractVersionRepository contractVersionRepository;
    private final GeneratedArtifactRepository generatedArtifactRepository;
    private final CompatibilityAnalyzer compatibilityAnalyzer;

    public CompatibilityService(
            CompatibilityReportRepository repository,
            ContractVersionRepository contractVersionRepository,
            GeneratedArtifactRepository generatedArtifactRepository,
            CompatibilityAnalyzer compatibilityAnalyzer
    ) {
        this.repository = repository;
        this.contractVersionRepository = contractVersionRepository;
        this.generatedArtifactRepository = generatedArtifactRepository;
        this.compatibilityAnalyzer = compatibilityAnalyzer;
    }

    @Transactional(readOnly = true)
    public List<CompatibilityReport> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public CompatibilityReport getLatestForContractVersion(Long contractVersionId) {
        if (contractVersionId == null) {
            throw new IllegalArgumentException("contractVersionId must not be null");
        }
        return repository.findTopByContractVersion_IdOrderByIdDesc(contractVersionId).orElse(null);
    }

    @Transactional
    public CompatibilityReport analyzeAndSave(Long contractVersionId) {
        ContractVersion current = contractVersionRepository.findById(contractVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Contract version not found: " + contractVersionId));
        CompatibilityResult result = analyze(current);
        return saveReport(current, result);
    }

    @Transactional(readOnly = true)
    public CompatibilityResult analyze(ContractVersion current) {
        ContractVersion baseline = resolvePublishedBaseline(current);
        return compatibilityAnalyzer.analyze(
                baseline == null ? null : baseline.getContent(),
                current.getContent(),
                current.getContract().getType()
        );
    }

    @Transactional
    public CompatibilityReport saveReport(ContractVersion contractVersion, CompatibilityResult result) {
        return repository.save(new CompatibilityReport(
                contractVersion,
                result.level(),
                result.recommendedSemverIncrement(),
                result.findingsAsJson(),
                migrationAdvice(result, contractVersion.getContract().getType())
        ));
    }

    private String migrationAdvice(CompatibilityResult compatibility, ContractType type) {
        if (compatibility.findings().stream().anyMatch(f -> "INITIAL_VERSION".equals(f.code()))) {
            return "Initial release detected. Publish as MINOR, announce baseline, and lock compatibility checks for the next revision.";
        }
        if (compatibility.level().name().equals("COMPATIBLE")) {
            return "Non-breaking changes detected. Bump " + compatibility.recommendedSemverIncrement()
                    + ", update changelog, and run consumer smoke tests before rollout.";
        }
        List<String> criticalLocations = compatibility.findings().stream()
                .filter(CompatibilityFinding::breaking)
                .filter(f -> f.severity() == ChangeSeverity.CRITICAL || f.severity() == ChangeSeverity.MAJOR)
                .map(CompatibilityFinding::location)
                .limit(3)
                .toList();
        String hotspots = criticalLocations.isEmpty() ? "key API elements" : String.join(", ", criticalLocations);
        if (type == ContractType.OPENAPI) {
            return "Breaking REST changes detected in " + hotspots
                    + ". Publish MAJOR, keep deprecated endpoint/field aliases for one transition window, and share migration examples with consumers.";
        }
        return "Breaking event-schema changes detected in " + hotspots
                + ". Publish MAJOR, version topic/subject names, and run dual-consumer mode until all consumers are migrated.";
    }

    private ContractVersion resolvePublishedBaseline(ContractVersion current) {
        if (current == null || current.getContract() == null || current.getContract().getId() == null) {
            return null;
        }
        return generatedArtifactRepository
                .findTopByJob_ContractVersion_Contract_IdAndJob_ContractVersion_IdLessThanOrderByJob_ContractVersion_IdDesc(
                        current.getContract().getId(),
                        current.getId()
                )
                .map(artifact -> artifact.getJob().getContractVersion())
                .orElse(null);
    }
}
