package ru.vkr.contracts.api.service;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.config.GenerationMetrics;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.GeneratedArtifact;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.shared.model.JobStatus;
import ru.vkr.contracts.worker.compat.CompatibilityResult;
import ru.vkr.contracts.worker.generation.TransientGenerationException;
import ru.vkr.contracts.worker.generation.openapi.OpenApiPipeline;
import ru.vkr.contracts.worker.generation.asyncapi.AsyncApiPipeline;
import ru.vkr.contracts.worker.generation.model.GenerationResult;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class GenerationJobProcessor {
    private static final Logger log = LoggerFactory.getLogger(GenerationJobProcessor.class);

    private final ContractVersionRepository contractVersionRepository;
    private final GenerationJobRepository generationJobRepository;
    private final GeneratedArtifactRepository generatedArtifactRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final CompatibilityService compatibilityService;
    private final GenerationMetrics generationMetrics;
    private final OpenApiPipeline openApiPipeline;
    private final AsyncApiPipeline asyncApiPipeline;
    private final int retryMaxAttempts;
    private final long retryBackoffMs;

    public GenerationJobProcessor(
            ContractVersionRepository contractVersionRepository,
            GenerationJobRepository generationJobRepository,
            GeneratedArtifactRepository generatedArtifactRepository,
            PublicationLogRepository publicationLogRepository,
            CompatibilityService compatibilityService,
            GenerationMetrics generationMetrics,
            OpenApiPipeline openApiPipeline,
            AsyncApiPipeline asyncApiPipeline,
            @Value("${generation.jobs.retry.max-attempts:3}") int retryMaxAttempts,
            @Value("${generation.jobs.retry.backoff-ms:500}") long retryBackoffMs
    ) {
        this.contractVersionRepository = contractVersionRepository;
        this.generationJobRepository = generationJobRepository;
        this.generatedArtifactRepository = generatedArtifactRepository;
        this.publicationLogRepository = publicationLogRepository;
        this.compatibilityService = compatibilityService;
        this.generationMetrics = generationMetrics;
        this.openApiPipeline = openApiPipeline;
        this.asyncApiPipeline = asyncApiPipeline;
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
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
        Instant startedAt = Instant.now();
        MDC.put("correlationId", job.getCorrelationId());
        publicationLogRepository.save(new PublicationLog(
                job,
                "PIPELINE",
                "RUNNING",
                "event=job-claimed; stage=claim",
                "JOB_CLAIMED",
                PublicationLog.ERROR_CATEGORY_NONE
        ));
        try {
            ContractVersion current = job.getContractVersion();
            log.info("Pipeline started for jobId={} contractVersionId={} type={}",
                    jobId, current.getId(), current.getContract().getType());
            ContractVersion previous = contractVersionRepository.findByContractOrderByIdDesc(current.getContract())
                    .stream()
                    .filter(v -> !v.getId().equals(current.getId()))
                    .findFirst()
                    .orElse(null);
            publicationLogRepository.save(new PublicationLog(
                    job,
                    "COMPATIBILITY",
                    "RUNNING",
                    "event=compatibility-started; stage=compatibility",
                    "COMPATIBILITY_STARTED",
                    PublicationLog.ERROR_CATEGORY_NONE
            ));
            CompatibilityResult compatibility = compatibilityService.analyze(current);
            publicationLogRepository.save(new PublicationLog(
                    job,
                    "COMPATIBILITY",
                    "SUCCESS",
                    "event=compatibility-finished; findings=" + compatibility.findings().size()
                            + "; level=" + compatibility.level()
                            + "; semver=" + compatibility.recommendedSemverIncrement(),
                    "COMPATIBILITY_FINISHED",
                    PublicationLog.ERROR_CATEGORY_NONE
            ));
            job.touch();

            publicationLogRepository.save(new PublicationLog(
                    job,
                    "PIPELINE",
                    "RUNNING",
                    "event=pipeline-started; stage=generation",
                    "PIPELINE_STARTED",
                    PublicationLog.ERROR_CATEGORY_NONE
            ));
            GenerationResult generationResult = runPipelineWithRetry(job, current, previous);
            generatedArtifactRepository.save(new GeneratedArtifact(
                    job,
                    generationResult.coordinates(),
                    generationResult.publicationUrl(),
                    generationResult.schemaSubject()
            ));
            publicationLogRepository.save(new PublicationLog(
                    job,
                    "NEXUS",
                    "SUCCESS",
                    "event=nexus-published; url=" + generationResult.publicationUrl(),
                    "NEXUS_PUBLISHED",
                    PublicationLog.ERROR_CATEGORY_NONE
            ));
            if (generationResult.schemaSubject() != null) {
                publicationLogRepository.save(new PublicationLog(
                        job,
                        "SCHEMA_REGISTRY",
                        "SUCCESS",
                        "event=schema-registered; subject=" + generationResult.schemaSubject(),
                        "SCHEMA_REGISTERED",
                        PublicationLog.ERROR_CATEGORY_NONE
                ));
            }
            publicationLogRepository.save(new PublicationLog(
                    job,
                    "PIPELINE",
                    "SUCCESS",
                    "event=artifact-persisted; coordinates=" + generationResult.coordinates(),
                    "ARTIFACT_PERSISTED",
                    PublicationLog.ERROR_CATEGORY_NONE
            ));
            job.markSuccess(generationResult.log());
            generationMetrics.incrementPipelineOutcome(current.getContract().getType(), "success");
            generationMetrics.recordPipelineDuration(
                    current.getContract().getType(),
                    "success",
                    Duration.between(startedAt, Instant.now())
            );
            log.info("Pipeline finished successfully for jobId={} contractVersionId={}", jobId, current.getId());
        } catch (Throwable t) {
            ContractType contractType = job.getContractVersion().getContract().getType();
            FailureType failureType = classifyFailure(t);
            String failureMessage = "Generation failed [" + failureType.label + "]: " + normalizeErrorMessage(t);
            generationMetrics.incrementPipelineOutcome(contractType, "failed");
            generationMetrics.recordPipelineDuration(
                    contractType,
                    "failed",
                    Duration.between(startedAt, Instant.now())
            );
            if (failureType == FailureType.RETRYABLE) {
                generationMetrics.incrementRetryNeeded(contractType, "retryable_failure");
            }
            publicationLogRepository.save(new PublicationLog(
                    job,
                    "PIPELINE",
                    failureType.status,
                    "event=pipeline-failed; category=" + failureType.errorCategory
                            + "; failureType=" + failureType.label
                            + "; maxAttempts=" + retryMaxAttempts
                            + "; message=" + failureMessage,
                    "PIPELINE_FAILED",
                    failureType.errorCategory
            ));
            job.markFailed(failureMessage);
            log.warn("Pipeline failed for jobId={} reason={}", jobId, failureMessage, t);
            if (t instanceof Error error) {
                throw error;
            }
        } finally {
            MDC.remove("correlationId");
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

    private GenerationResult runPipelineWithRetry(GenerationJob job, ContractVersion current, ContractVersion previous) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                return runPipeline(current, previous);
            } catch (RuntimeException e) {
                lastFailure = e;
                FailureType failureType = classifyFailure(e);
                boolean hasNextAttempt = attempt < retryMaxAttempts;
                if (failureType != FailureType.RETRYABLE || !hasNextAttempt) {
                    throw e;
                }

                int nextAttempt = attempt + 1;
                long delayMs = retryBackoffMs * attempt;
                generationMetrics.incrementRetryNeeded(current.getContract().getType(), "retryable_failure");
                publicationLogRepository.save(new PublicationLog(
                        job,
                        "PIPELINE",
                        "RETRYING",
                        "event=pipeline-retry-scheduled; attempt=" + attempt + "/" + retryMaxAttempts
                                + "; nextAttempt=" + nextAttempt
                                + "; delayMs=" + delayMs
                                + "; reason=" + normalizeErrorMessage(e),
                        "PIPELINE_RETRY_SCHEDULED",
                        PublicationLog.ERROR_CATEGORY_TECHNICAL
                ));
                sleepBackoff(delayMs, job.getId(), attempt, retryMaxAttempts);
            }
        }
        throw lastFailure == null
                ? new IllegalStateException("Unexpected pipeline retry state")
                : lastFailure;
    }

    private void sleepBackoff(long delayMs, Long jobId, int attempt, int maxAttempts) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new TransientGenerationException(
                    "Retry backoff interrupted for jobId=" + jobId
                            + " at attempt " + attempt + "/" + maxAttempts,
                    interruptedException
            );
        }
    }

    private FailureType classifyFailure(Throwable e) {
        String message = normalizeErrorMessage(e).toLowerCase();
        if (message.contains("compatibility conflict") || message.contains("incompatible")) {
            return FailureType.BUSINESS_INCOMPATIBLE;
        }
        if (e instanceof IllegalArgumentException) {
            return FailureType.NON_RETRYABLE;
        }
        if (e instanceof TransientDataAccessException || e instanceof TimeoutException || containsTransientException(e)) {
            return FailureType.RETRYABLE;
        }
        return FailureType.NON_RETRYABLE;
    }

    private boolean containsTransientException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof TransientGenerationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeErrorMessage(Throwable e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }

    private enum FailureType {
        RETRYABLE("retryable", "FAILED_RETRYABLE", PublicationLog.ERROR_CATEGORY_TECHNICAL),
        NON_RETRYABLE("non-retryable", "FAILED_NON_RETRYABLE", PublicationLog.ERROR_CATEGORY_TECHNICAL),
        BUSINESS_INCOMPATIBLE("business-incompatible", "FAILED_BUSINESS_INCOMPATIBLE", PublicationLog.ERROR_CATEGORY_BUSINESS);

        private final String label;
        private final String status;
        private final String errorCategory;

        FailureType(String label, String status, String errorCategory) {
            this.label = label;
            this.status = status;
            this.errorCategory = errorCategory;
        }
    }
}
