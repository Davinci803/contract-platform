package ru.vkr.contracts.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.JobStatus;

import java.time.Instant;
import java.util.List;

@Component
public class GenerationJobRecoveryService {
    private final GenerationJobRepository generationJobRepository;
    private final PublicationLogRepository publicationLogRepository;
    private final boolean recoveryEnabled;
    private final long staleThresholdMs;

    public GenerationJobRecoveryService(
            GenerationJobRepository generationJobRepository,
            PublicationLogRepository publicationLogRepository,
            @Value("${generation.jobs.recovery.enabled:true}") boolean recoveryEnabled,
            @Value("${generation.jobs.recovery.stale-threshold-ms:600000}") long staleThresholdMs
    ) {
        this.generationJobRepository = generationJobRepository;
        this.publicationLogRepository = publicationLogRepository;
        this.recoveryEnabled = recoveryEnabled;
        this.staleThresholdMs = staleThresholdMs;
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
        }
    }
}
