package ru.vkr.contracts.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.EntityContract;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.dto.JobResponse;
import ru.vkr.contracts.api.repo.CompatibilityReportRepository;
import ru.vkr.contracts.api.repo.ContractRepository;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.shared.model.JobStatus;
import ru.vkr.contracts.worker.generation.openapi.OpenApiPipeline;
import ru.vkr.contracts.worker.generation.model.GenerationResult;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GenerationJobProcessorIntegrationTest {
    @Autowired
    private GenerationJobService generationJobService;
    @Autowired
    private GenerationJobProcessor generationJobProcessor;
    @Autowired
    private GenerationJobRecoveryService generationJobRecoveryService;
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private ContractVersionRepository contractVersionRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
    @Autowired
    private GeneratedArtifactRepository generatedArtifactRepository;
    @Autowired
    private PublicationLogRepository publicationLogRepository;
    @Autowired
    private CompatibilityReportRepository compatibilityReportRepository;

    @MockBean
    private OpenApiPipeline openApiPipeline;

    @BeforeEach
    void cleanup() {
        publicationLogRepository.deleteAll();
        generatedArtifactRepository.deleteAll();
        generationJobRepository.deleteAll();
        compatibilityReportRepository.deleteAll();
        contractVersionRepository.deleteAll();
        contractRepository.deleteAll();
    }

    @Test
    void shouldCompleteJobInBackground() {
        when(openApiPipeline.generateAndPublish(anyString(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(new GenerationResult(
                        "ru.vkr.contracts.generated",
                        "payment-api-rest-client",
                        "1.0.0",
                        "ru.vkr.contracts.generated:payment-api-rest-client:1.0.0",
                        "http://localhost:8081/repository/maven-releases/payment-api-rest-client/1.0.0",
                        null,
                        "Pipeline completed successfully"
                ));

        ContractVersion version = createOpenApiVersion("openapi: 3.0.1\npaths:\n  /payments:\n    get: {}");
        JobResponse createdJob = generationJobService.create(version.getId());

        JobResponse completedJob = awaitTerminalStatus(createdJob.jobId(), Duration.ofSeconds(8));

        assertEquals(JobStatus.SUCCESS, completedJob.status());
        assertEquals(1, generatedArtifactRepository.countByJob_Id(createdJob.jobId()));
        assertEquals(1, compatibilityReportRepository.countByContractVersion_Id(version.getId()));
    }

    @Test
    void shouldMarkJobAsFailedWhenPipelineThrowsException() {
        when(openApiPipeline.generateAndPublish(anyString(), anyString(), anyString(), nullable(String.class)))
                .thenThrow(new IllegalArgumentException("OpenAPI signature not found"));

        ContractVersion version = createOpenApiVersion("invalid openapi");
        JobResponse createdJob = generationJobService.create(version.getId());

        JobResponse completedJob = awaitTerminalStatus(createdJob.jobId(), Duration.ofSeconds(8));

        assertEquals(JobStatus.FAILED, completedJob.status());
        assertTrue(completedJob.log().contains("non-retryable"));
        assertEquals(0, generatedArtifactRepository.countByJob_Id(createdJob.jobId()));
        assertEquals(1, publicationLogRepository.countByJob_IdAndStatus(createdJob.jobId(), "FAILED_NON_RETRYABLE"));
    }

    @Test
    void shouldIgnoreSecondProcessingAttemptForSameJob() {
        when(openApiPipeline.generateAndPublish(anyString(), anyString(), anyString(), nullable(String.class)))
                .thenReturn(new GenerationResult(
                        "ru.vkr.contracts.generated",
                        "payment-api-rest-client",
                        "1.0.0",
                        "ru.vkr.contracts.generated:payment-api-rest-client:1.0.0",
                        "http://localhost:8081/repository/maven-releases/payment-api-rest-client/1.0.0",
                        null,
                        "Pipeline completed successfully"
                ));

        ContractVersion version = createOpenApiVersion("openapi: 3.0.1\npaths:\n  /payments:\n    get: {}");
        GenerationJob job = generationJobRepository.save(new GenerationJob(version));

        boolean firstAttemptProcessed = generationJobProcessor.processNow(job.getId());
        boolean secondAttemptProcessed = generationJobProcessor.processNow(job.getId());

        JobResponse finalState = generationJobService.get(job.getId());
        assertTrue(firstAttemptProcessed);
        assertFalse(secondAttemptProcessed);
        assertEquals(JobStatus.SUCCESS, finalState.status());
        assertEquals(1, generatedArtifactRepository.countByJob_Id(job.getId()));
        assertEquals(1, publicationLogRepository.countByJob_IdAndStatus(job.getId(), "SUCCESS"));
    }

    @Test
    @Transactional
    void shouldRecoverStaleRunningJob() {
        ContractVersion version = createOpenApiVersion("openapi: 3.0.1\npaths:\n  /payments:\n    get: {}");
        GenerationJob job = generationJobRepository.save(new GenerationJob(version));
        generationJobRepository.updateStatusAndUpdatedAt(
                job.getId(),
                JobStatus.RUNNING,
                Instant.now().minus(Duration.ofHours(2))
        );

        generationJobRecoveryService.recoverStuckJobs();
        JobResponse recoveredJob = generationJobService.get(job.getId());

        assertEquals(JobStatus.FAILED, recoveredJob.status());
        assertTrue(recoveredJob.log().contains("watchdog"));
        assertEquals(1, publicationLogRepository.countByJob_IdAndTargetAndStatus(
                job.getId(),
                "RECOVERY",
                "FAILED_NON_RETRYABLE"
        ));
    }

    private ContractVersion createOpenApiVersion(String content) {
        EntityContract contract = contractRepository.save(new EntityContract(
                "Payment API " + UUID.randomUUID(),
                ContractType.OPENAPI
        ));
        return contractVersionRepository.save(new ContractVersion(
                contract,
                "1.0.0",
                content,
                "tester"
        ));
    }

    private JobResponse awaitTerminalStatus(Long jobId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            JobResponse response = generationJobService.get(jobId);
            if (response.status() == JobStatus.SUCCESS || response.status() == JobStatus.FAILED) {
                return response;
            }
            sleepSilently();
        }
        fail("Job did not reach terminal status within timeout");
        return null;
    }

    private void sleepSilently() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for async job completion");
        }
    }
}
