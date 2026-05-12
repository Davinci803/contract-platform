package ru.vkr.contracts.api.service;

import org.springframework.core.task.TaskRejectedException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.config.GenerationMetrics;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.dto.JobResponse;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;

@Service
public class GenerationJobService {
    private final ContractVersionRepository contractVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final GenerationJobProcessor generationJobProcessor;
    private final GenerationMetrics generationMetrics;

    public GenerationJobService(
            ContractVersionRepository contractVersionRepository,
            GenerationJobRepository generationJobRepository,
            PublicationLogRepository publicationLogRepository,
            GenerationJobProcessor generationJobProcessor,
            GenerationMetrics generationMetrics
    ) {
        this.contractVersionRepository = contractVersionRepository;
        this.generationJobRepository = generationJobRepository;
        this.publicationLogRepository = publicationLogRepository;
        this.generationJobProcessor = generationJobProcessor;
        this.generationMetrics = generationMetrics;
    }

    @Transactional
    public JobResponse create(Long contractVersionId) {
        ContractVersion contractVersion = contractVersionRepository.findById(contractVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Contract version not found: " + contractVersionId));
        String correlationId = MDC.get("correlationId");
        GenerationJob job = generationJobRepository.save(new GenerationJob(contractVersion, correlationId));
        publicationLogRepository.save(new PublicationLog(
                job,
                "PIPELINE",
                "PENDING",
                "event=job-created; contractVersionId=" + contractVersionId,
                "JOB_CREATED",
                PublicationLog.ERROR_CATEGORY_NONE
        ));
        try {
            generationJobProcessor.processAsync(job.getId());
        } catch (TaskRejectedException e) {
            String message = "Generation queue is overloaded. Please retry later.";
            generationMetrics.incrementRetryNeeded(contractVersion.getContract().getType(), "queue_rejected");
            publicationLogRepository.save(new PublicationLog(
                    job,
                    "PIPELINE",
                    "FAILED_RETRYABLE",
                    "event=queue-rejected; message=" + message,
                    "QUEUE_REJECTED",
                    PublicationLog.ERROR_CATEGORY_TECHNICAL
            ));
            job.markFailed(message);
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return generationJobRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    private JobResponse toResponse(GenerationJob job) {
        return new JobResponse(job.getId(), job.getContractVersion().getId(), job.getStatus(), job.getLog());
    }
}
