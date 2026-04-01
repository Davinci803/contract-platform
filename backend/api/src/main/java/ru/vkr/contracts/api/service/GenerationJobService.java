package ru.vkr.contracts.api.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.*;
import ru.vkr.contracts.api.dto.JobResponse;
import ru.vkr.contracts.api.repo.*;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.worker.compat.CompatibilityAnalyzer;
import ru.vkr.contracts.worker.compat.CompatibilityResult;
import ru.vkr.contracts.worker.generation.AsyncApiPipeline;
import ru.vkr.contracts.worker.generation.GenerationResult;
import ru.vkr.contracts.worker.generation.OpenApiPipeline;

@Service
public class GenerationJobService {
    private final ContractVersionRepository contractVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GeneratedArtifactRepository generatedArtifactRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final CompatibilityReportRepository compatibilityReportRepository;
    private final CompatibilityAnalyzer compatibilityAnalyzer;
    private final OpenApiPipeline openApiPipeline;
    private final AsyncApiPipeline asyncApiPipeline;

    public GenerationJobService(
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

    @Transactional
    public JobResponse create(Long contractVersionId) {
        ContractVersion contractVersion = contractVersionRepository.findById(contractVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Contract version not found: " + contractVersionId));
        GenerationJob job = generationJobRepository.save(new GenerationJob(contractVersion));
        processAsync(job.getId());
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return generationJobRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    @Async
    @Transactional
    public void processAsync(Long jobId) {
        GenerationJob job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        try {
            job.markRunning();
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

            GenerationResult generationResult = runPipeline(current);
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
        } catch (Exception e) {
            publicationLogRepository.save(new PublicationLog(job, "PIPELINE", "FAILED", e.getMessage()));
            job.markFailed("Generation failed: " + e.getMessage());
        }
    }

    private GenerationResult runPipeline(ContractVersion version) {
        String contractName = version.getContract().getName();
        return switch (version.getContract().getType()) {
            case OPENAPI -> openApiPipeline.generateAndPublish(contractName, version.getVersion(), version.getContent());
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

    private JobResponse toResponse(GenerationJob job) {
        return new JobResponse(job.getId(), job.getContractVersion().getId(), job.getStatus(), job.getLog());
    }
}
