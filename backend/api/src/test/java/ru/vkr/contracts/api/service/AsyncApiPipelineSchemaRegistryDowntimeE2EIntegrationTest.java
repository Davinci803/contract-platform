package ru.vkr.contracts.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.EntityContract;
import ru.vkr.contracts.api.dto.JobResponse;
import ru.vkr.contracts.api.repo.CompatibilityReportRepository;
import ru.vkr.contracts.api.repo.ContractRepository;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.shared.model.JobStatus;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class AsyncApiPipelineSchemaRegistryDowntimeE2EIntegrationTest {
    @Autowired
    private GenerationJobService generationJobService;
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

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("generation.async-api.nexus.base-url", () -> "http://127.0.0.1:1");
        registry.add("generation.async-api.nexus.repository", () -> "maven-releases");
        registry.add("generation.async-api.nexus.username", () -> "");
        registry.add("generation.async-api.nexus.password", () -> "");
        registry.add("generation.async-api.schema-registry.base-url", () -> "http://127.0.0.1:1");
        registry.add("generation.async-api.schema-registry.compatibility", () -> "BACKWARD");
        registry.add("generation.async-api.schema-registry.subject-suffix", () -> "value");
    }

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
    void shouldFailAsyncApiPipelineWhenSchemaRegistryIsUnavailableWithoutMocks() {
        ContractVersion version = createVersion(
                "Payments Async Registry Down",
                validAsyncApi()
        );

        JobResponse createdJob = generationJobService.create(version.getId());
        JobResponse completedJob = awaitTerminalStatus(createdJob.jobId(), Duration.ofSeconds(30));

        assertEquals(JobStatus.FAILED, completedJob.status());
        assertTrue(completedJob.log().contains("Schema Registry"));
        assertTrue(completedJob.log().contains("compensation=not_required"));
        assertEquals(0, generatedArtifactRepository.countByJob_Id(createdJob.jobId()));
        assertEquals(1, publicationLogRepository.countByJob_IdAndStatus(createdJob.jobId(), "FAILED_RETRYABLE"));
        assertEquals(2, publicationLogRepository.countByJob_IdAndStatus(createdJob.jobId(), "RETRYING"));
    }

    private ContractVersion createVersion(String name, String content) {
        EntityContract contract = contractRepository.save(new EntityContract(name, ContractType.ASYNCAPI));
        return contractVersionRepository.save(new ContractVersion(contract, "1.0.0", content, "tester"));
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

    private String validAsyncApi() {
        return """
                asyncapi: 2.6.0
                info:
                  title: Payments Async API
                  version: 1.0.0
                channels:
                  payments/created:
                    publish:
                      message:
                        name: PaymentCreated
                        payload:
                          type: object
                          properties:
                            paymentId:
                              type: string
                            amount:
                              type: number
                """;
    }
}
