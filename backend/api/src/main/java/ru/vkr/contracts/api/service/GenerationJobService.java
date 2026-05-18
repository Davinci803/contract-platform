package ru.vkr.contracts.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.dto.JobResponse;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;

@Service
public class GenerationJobService {
    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private final ContractVersionRepository contractVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final GenerationJobProcessor generationJobProcessor;

    public GenerationJobService(
            ContractVersionRepository contractVersionRepository,
            GenerationJobRepository generationJobRepository,
            PublicationLogRepository publicationLogRepository,
            GenerationJobProcessor generationJobProcessor
    ) {
        this.contractVersionRepository = contractVersionRepository;
        this.generationJobRepository = generationJobRepository;
        this.publicationLogRepository = publicationLogRepository;
        this.generationJobProcessor = generationJobProcessor;
    }

    @Transactional
    public JobResponse create(Long contractVersionId, boolean publishInNewMajorSubject) {
        ContractVersion contractVersion = contractVersionRepository.findById(contractVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Contract version not found: " + contractVersionId));
        String correlationId = MDC.get("correlationId");
        GenerationJob job = generationJobRepository.save(new GenerationJob(contractVersion, correlationId, publishInNewMajorSubject));
        publicationLogRepository.save(new PublicationLog(
                job,
                "PIPELINE",
                "PENDING",
                "event=job-created; contractVersionId=" + contractVersionId
                        + "; publishNewMajorSubject=" + publishInNewMajorSubject,
                "JOB_CREATED",
                PublicationLog.ERROR_CATEGORY_NONE
        ));
        Long jobId = job.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    generationJobProcessor.processAsync(jobId);
                } catch (RuntimeException asyncDispatchError) {
                    // Fallback ensures the job is not stuck in PENDING due to async dispatch problems.
                    log.warn("Async dispatch failed for jobId={}, running sync fallback", jobId, asyncDispatchError);
                    generationJobProcessor.processNow(jobId);
                }
            }
        });
        return toResponse(job);
    }

    @Transactional
    public JobResponse create(Long contractVersionId) {
        return create(contractVersionId, false);
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long id) {
        return generationJobRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    @Transactional(readOnly = true)
    public JobResponse getByCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("Correlation id must not be blank");
        }
        return generationJobRepository.findTopByCorrelationIdOrderByIdDesc(correlationId.trim()).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Job not found for correlationId: " + correlationId));
    }

    private JobResponse toResponse(GenerationJob job) {
        return new JobResponse(
                job.getId(),
                job.getContractVersion().getId(),
                job.getCorrelationId(),
                job.getStatus(),
                job.getLog()
        );
    }
}
