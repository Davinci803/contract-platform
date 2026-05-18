package ru.vkr.contracts.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.config.GenerationMetrics;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.JobStatus;

import java.time.Instant;
import java.util.List;

@Component
public class GenerationJobRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(GenerationJobRecoveryService.class);

    private final GenerationJobRepository generationJobRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final GenerationMetrics generationMetrics;
    private final GenerationJobProcessor generationJobProcessor;
    private final boolean recoveryEnabled;
    private final long staleThresholdMs;
    private final long pendingThresholdMs;

    public GenerationJobRecoveryService(
            GenerationJobRepository generationJobRepository,
            PublicationLogRepository publicationLogRepository,
            GenerationMetrics generationMetrics,
            GenerationJobProcessor generationJobProcessor,
            @Value("${generation.jobs.recovery.enabled:true}") boolean recoveryEnabled,
            @Value("${generation.jobs.recovery.stale-threshold-ms:600000}") long staleThresholdMs,
            @Value("${generation.jobs.recovery.pending-threshold-ms:30000}") long pendingThresholdMs
    ) {
        this.generationJobRepository = generationJobRepository;
        this.publicationLogRepository = publicationLogRepository;
        this.generationMetrics = generationMetrics;
        this.generationJobProcessor = generationJobProcessor;
        this.recoveryEnabled = recoveryEnabled;
        this.staleThresholdMs = staleThresholdMs;
        this.pendingThresholdMs = pendingThresholdMs;
    }

    @Scheduled(fixedDelayString = "${generation.jobs.recovery.scan-interval-ms:60000}")
    @Transactional
    public void recoverStuckJobs() {
        if (!recoveryEnabled) {
            return;
        }

        Instant staleBefore = Instant.now().minusMillis(staleThresholdMs);
        List<GenerationJob> staleRunningJobs = generationJobRepository.findByStatusAndUpdatedAtBefore(JobStatus.RUNNING, staleBefore);
        for (GenerationJob job : staleRunningJobs) {
            MDC.put("correlationId", job.getCorrelationId());
            try {
                String message = "Generation watchdog marked stale RUNNING job as FAILED after timeout.";
                publicationLogRepository.save(new PublicationLog(
                        job,
                        "RECOVERY",
                        "FAILED_NON_RETRYABLE",
                        "event=watchdog-timeout; message=" + message,
                        "WATCHDOG_TIMEOUT",
                        PublicationLog.ERROR_CATEGORY_TECHNICAL
                ));
                job.markFailed(message);
                generationMetrics.incrementRetryNeeded(job.getContractVersion().getContract().getType(), "watchdog_timeout");
                log.warn("Watchdog marked stale job as failed: jobId={} correlationId={}", job.getId(), job.getCorrelationId());
            } finally {
                MDC.remove("correlationId");
            }
        }

        Instant pendingBefore = Instant.now().minusMillis(pendingThresholdMs);
        List<GenerationJob> stalePendingJobs = generationJobRepository.findByStatusAndCreatedAtBefore(JobStatus.PENDING, pendingBefore);
        for (GenerationJob job : stalePendingJobs) {
            MDC.put("correlationId", job.getCorrelationId());
            try {
                boolean claimed = generationJobProcessor.processNow(job.getId());
                if (claimed) {
                    log.info("Watchdog picked stale PENDING job: jobId={} correlationId={}", job.getId(), job.getCorrelationId());
                }
            } finally {
                MDC.remove("correlationId");
            }
        }
    }
}
