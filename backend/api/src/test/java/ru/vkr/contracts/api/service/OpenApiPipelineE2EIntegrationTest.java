package ru.vkr.contracts.api.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiPipelineE2EIntegrationTest {
    private static final int NEXUS_STUB_PORT = findFreePort();
    private static final Set<String> uploadedPaths = ConcurrentHashMap.newKeySet();
    private static HttpServer nexusStubServer;

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
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("generation.open-api.nexus.base-url", () -> "http://localhost:" + NEXUS_STUB_PORT);
        registry.add("generation.open-api.nexus.repository", () -> "maven-releases");
        registry.add("generation.open-api.nexus.username", () -> "");
        registry.add("generation.open-api.nexus.password", () -> "");
    }

    @BeforeAll
    static void startNexusStub() throws IOException {
        nexusStubServer = HttpServer.create(new InetSocketAddress(NEXUS_STUB_PORT), 0);
        nexusStubServer.createContext("/", OpenApiPipelineE2EIntegrationTest::handleUpload);
        nexusStubServer.start();
    }

    @AfterAll
    static void stopNexusStub() {
        if (nexusStubServer != null) {
            nexusStubServer.stop(0);
        }
    }

    @BeforeEach
    void cleanup() {
        uploadedPaths.clear();
        publicationLogRepository.deleteAll();
        generatedArtifactRepository.deleteAll();
        generationJobRepository.deleteAll();
        compatibilityReportRepository.deleteAll();
        contractVersionRepository.deleteAll();
        contractRepository.deleteAll();
    }

    @Test
    void shouldRunOpenApiPipelineEndToEndAndPersistRealArtifactMetadata() {
        ContractVersion version = createVersion(
                "Payment API E2E",
                validOpenApi("/payments")
        );

        JobResponse createdJob = generationJobService.create(version.getId());
        JobResponse completedJob = awaitTerminalStatus(createdJob.jobId(), Duration.ofSeconds(10));

        assertEquals(JobStatus.SUCCESS, completedJob.status());
        assertTrue(completedJob.log().contains("[publish] uploaded jar and pom"));
        assertEquals(1, generatedArtifactRepository.countByJob_Id(createdJob.jobId()));
        assertEquals(1, publicationLogRepository.countByJob_IdAndStatus(createdJob.jobId(), "SUCCESS"));
        assertTrue(uploadedPaths.stream().anyMatch(path -> path.endsWith(".jar")));
        assertTrue(uploadedPaths.stream().anyMatch(path -> path.endsWith(".pom")));

        Map<String, Object> artifactRow = jdbcTemplate.queryForMap(
                "select coordinates, publication_url from generated_artifacts where job_id = ?",
                createdJob.jobId()
        );
        assertTrue(String.valueOf(artifactRow.get("coordinates")).contains(":"));
        assertTrue(String.valueOf(artifactRow.get("publication_url"))
                .startsWith("http://localhost:" + NEXUS_STUB_PORT + "/repository/maven-releases/"));
    }

    @Test
    void shouldFailOpenApiPipelineForInvalidSpecificationWithoutMocks() {
        ContractVersion version = createVersion(
                "Broken API E2E",
                "openapi: bad"
        );

        JobResponse createdJob = generationJobService.create(version.getId());
        JobResponse completedJob = awaitTerminalStatus(createdJob.jobId(), Duration.ofSeconds(10));

        assertEquals(JobStatus.FAILED, completedJob.status());
        assertTrue(completedJob.log().contains("OpenAPI"));
        assertEquals(0, generatedArtifactRepository.countByJob_Id(createdJob.jobId()));
        assertEquals(1, publicationLogRepository.countByJob_IdAndStatus(createdJob.jobId(), "FAILED_NON_RETRYABLE"));
    }

    private ContractVersion createVersion(String name, String content) {
        EntityContract contract = contractRepository.save(new EntityContract(name, ContractType.OPENAPI));
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

    private static void handleUpload(HttpExchange exchange) throws IOException {
        uploadedPaths.add(exchange.getRequestURI().getPath());
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(201, -1);
        exchange.close();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate free port for test Nexus stub", e);
        }
    }

    private String validOpenApi(String path) {
        return """
                openapi: 3.0.1
                info:
                  title: Payment API
                  version: 1.0.0
                paths:
                  %s:
                    get:
                      operationId: getPayment
                      responses:
                        "200":
                          description: ok
                components:
                  schemas:
                    Payment:
                      type: object
                      properties:
                        id:
                          type: string
                        amount:
                          type: number
                """.formatted(path);
    }
}
