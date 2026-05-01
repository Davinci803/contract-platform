package ru.vkr.contracts.api.service;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.CompatibilityReport;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.GeneratedArtifact;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.repo.CompatibilityReportRepository;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.shared.model.JobStatus;
import ru.vkr.contracts.worker.compat.CompatibilityAnalyzer;
import ru.vkr.contracts.worker.compat.CompatibilityResult;
import ru.vkr.contracts.worker.generation.openapi.OpenApiPipeline;
import ru.vkr.contracts.worker.generation.asyncapi.AsyncApiPipeline;
import ru.vkr.contracts.worker.generation.model.GenerationResult;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Component
public class GenerationJobProcessor {
    private final ContractVersionRepository contractVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GeneratedArtifactRepository generatedArtifactRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final CompatibilityReportRepository compatibilityReportRepository;
    private final CompatibilityAnalyzer compatibilityAnalyzer;
    private final OpenApiPipeline openApiPipeline;
    private final AsyncApiPipeline asyncApiPipeline;

    public GenerationJobProcessor(
            ContractVersionRepository contractVersionRepository,
            GenerationJobRepository generationJobRepository,
            GeneratedArtifactRepository generatedArtifactRepository,
            PublicationLogRepository publicationLogRepository,
            CompatibilityReportRepository compatibilityReportRepository,
            CompatibilityAnalyzer compatibilityAnalyzer,
            OpenApiPipeline openApiPipeline,
            AsyncApiPipeline asyncApiPipeline
    ) {
        this.contractVersionRepository = contractVersionRepository;
        this.generationJobRepository = generationJobRepository;
        this.generatedArtifactRepository = generatedArtifactRepository;
        this.publicationLogRepository = publicationLogRepository;
        this.compatibilityReportRepository = compatibilityReportRepository;
        this.compatibilityAnalyzer = compatibilityAnalyzer;
        this.openApiPipeline = openApiPipeline;
        this.asyncApiPipeline = asyncApiPipeline;
    }

    @Async("generationTaskExecutor")
    @Transactional
    public void processAsync(Long jobId) {
        processInternal(jobId);
    }

    @Transactional
    public boolean processNow(Long jobId) {
        return processInternal(jobId);
    }

    private boolean processInternal(Long jobId) {
        boolean claimed = generationJobRepository.updateStatusIfCurrent(
                jobId,
                JobStatus.PENDING,
                JobStatus.RUNNING,
                Instant.now()
        ) == 1;
        if (!claimed) {
            return false;
        }

        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        try {
            ContractVersion current = job.getContractVersion();
            ContractVersion previous = contractVersionRepository.findByContractOrderByIdDesc(current.getContract())
                    .stream()
                    .filter(v -> !v.getId().equals(current.getId()))
                    .findFirst()
                    .orElse(null);
            CompatibilityResult compatibility = compatibilityAnalyzer.analyze(
                    previous == null ? null : previous.getContent(),
                    current.getContent()
            );
            compatibilityReportRepository.save(new CompatibilityReport(
                    current,
                    compatibility.level(),
                    compatibility.recommendedSemverIncrement(),
                    String.join("; ", compatibility.findings()),
                    migrationAdvice(compatibility, current.getContract().getType())
            ));
            job.touch();

            GenerationResult generationResult = runPipeline(current, previous);
            generatedArtifactRepository.save(new GeneratedArtifact(
                    job,
                    generationResult.coordinates(),
                    generationResult.publicationUrl(),
                    generationResult.schemaSubject()
            ));
            publicationLogRepository.save(new PublicationLog(job, "NEXUS", "SUCCESS", generationResult.publicationUrl()));
            if (generationResult.schemaSubject() != null) {
                publicationLogRepository.save(new PublicationLog(job, "SCHEMA_REGISTRY", "SUCCESS", generationResult.schemaSubject()));
            }
            job.markSuccess(generationResult.log());
        } catch (Throwable t) {
            FailureType failureType = classifyFailure(t);
            String failureMessage = "Generation failed [" + failureType.label + "]: " + normalizeErrorMessage(t);
            publicationLogRepository.save(new PublicationLog(job, "PIPELINE", failureType.status, failureMessage));
            job.markFailed(failureMessage);
            if (t instanceof Error error) {
                throw error;
            }
        }
        return true;
    }

    private GenerationResult runPipeline(ContractVersion version, ContractVersion previousVersion) {
        String contractName = version.getContract().getName();
        String previousContent = previousVersion == null ? null : previousVersion.getContent();
        return switch (version.getContract().getType()) {
            case OPENAPI -> openApiPipeline.generateAndPublish(
                    contractName,
                    version.getVersion(),
                    version.getContent(),
                    previousContent
            );
            case ASYNCAPI -> asyncApiPipeline.generateAndPublish(contractName, version.getVersion(), version.getContent());
        };
    }

    private String migrationAdvice(CompatibilityResult compatibility, ContractType type) {
        if (compatibility.level().name().equals("COMPATIBLE")) {
            return "Compatible change detected. Prefer MINOR release and keep rollout notes.";
        }
        if (type == ContractType.OPENAPI) {
            return "Breaking REST change. Deprecate old fields/endpoints and publish MAJOR with transition period.";
        }
        return "Breaking event schema. Use new topic/version suffix and keep backward-compatible consumers during migration.";
    }

    private FailureType classifyFailure(Throwable e) {
        if (e instanceof IllegalArgumentException) {
            return FailureType.NON_RETRYABLE;
        }
        if (e instanceof TransientDataAccessException || e instanceof TimeoutException) {
            return FailureType.RETRYABLE;
        }
        return FailureType.NON_RETRYABLE;
    }

    private String normalizeErrorMessage(Throwable e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }

    private enum FailureType {
        RETRYABLE("retryable", "FAILED_RETRYABLE"),
        NON_RETRYABLE("non-retryable", "FAILED_NON_RETRYABLE");

        private final String label;
        private final String status;

        FailureType(String label, String status) {
            this.label = label;
            this.status = status;
        }
    }
}
