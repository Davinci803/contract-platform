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
class AsyncApiPipelineE2EIntegrationTest {
    private static final int NEXUS_STUB_PORT = findFreePort();
    private static final int REGISTRY_STUB_PORT = findFreePort();
    private static final Set<String> nexusUploads = ConcurrentHashMap.newKeySet();
    private static final Set<String> registryRequests = ConcurrentHashMap.newKeySet();
    private static HttpServer nexusStubServer;
    private static HttpServer registryStubServer;

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
        registry.add("generation.async-api.nexus.base-url", () -> "http://localhost:" + NEXUS_STUB_PORT);
        registry.add("generation.async-api.nexus.repository", () -> "maven-releases");
        registry.add("generation.async-api.nexus.username", () -> "");
        registry.add("generation.async-api.nexus.password", () -> "");
        registry.add("generation.async-api.schema-registry.base-url", () -> "http://localhost:" + REGISTRY_STUB_PORT);
        registry.add("generation.async-api.schema-registry.compatibility", () -> "BACKWARD");
        registry.add("generation.async-api.schema-registry.subject-suffix", () -> "value");
    }

    @BeforeAll
    static void startStubs() throws IOException {
        nexusStubServer = HttpServer.create(new InetSocketAddress(NEXUS_STUB_PORT), 0);
        nexusStubServer.createContext("/", AsyncApiPipelineE2EIntegrationTest::handleNexusUpload);
        nexusStubServer.start();

        registryStubServer = HttpServer.create(new InetSocketAddress(REGISTRY_STUB_PORT), 0);
        registryStubServer.createContext("/", AsyncApiPipelineE2EIntegrationTest::handleRegistry);
        registryStubServer.start();
    }

    @AfterAll
    static void stopStubs() {
        if (nexusStubServer != null) {
            nexusStubServer.stop(0);
        }
        if (registryStubServer != null) {
            registryStubServer.stop(0);
        }
    }

    @BeforeEach
    void cleanup() {
        nexusUploads.clear();
        registryRequests.clear();
        publicationLogRepository.deleteAll();
        generatedArtifactRepository.deleteAll();
        generationJobRepository.deleteAll();
        compatibilityReportRepository.deleteAll();
        contractVersionRepository.deleteAll();
        contractRepository.deleteAll();
    }

    @Test
    void shouldRunAsyncApiPipelineEndToEndAndPersistSchemaSubject() {
        ContractVersion version = createVersion("Payments Async E2E", validAsyncApi());

        JobResponse createdJob = generationJobService.create(version.getId());
        JobResponse completedJob = awaitTerminalStatus(createdJob.jobId(), Duration.ofSeconds(30));

        assertEquals(JobStatus.SUCCESS, completedJob.status());
        assertTrue(completedJob.log().contains("[schema-registry]"));
        assertEquals(1, generatedArtifactRepository.countByJob_Id(createdJob.jobId()));
        assertEquals(1, publicationLogRepository.countByJob_IdAndTargetAndStatus(createdJob.jobId(), "NEXUS", "SUCCESS"));
        assertEquals(1, publicationLogRepository.countByJob_IdAndTargetAndStatus(createdJob.jobId(), "SCHEMA_REGISTRY", "SUCCESS"));
        assertTrue(nexusUploads.stream().anyMatch(path -> path.endsWith(".jar")));
        assertTrue(registryRequests.stream().anyMatch(path -> path.startsWith("/subjects/")));

        Map<String, Object> artifactRow = jdbcTemplate.queryForMap(
                "select schema_subject, publication_url from generated_artifacts where job_id = ?",
                createdJob.jobId()
        );
        assertTrue(String.valueOf(artifactRow.get("schema_subject")).contains("payments.async.e2e"));
        assertTrue(String.valueOf(artifactRow.get("publication_url"))
                .startsWith("http://localhost:" + NEXUS_STUB_PORT + "/repository/maven-releases/"));
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

    private static void handleNexusUpload(HttpExchange exchange) throws IOException {
        nexusUploads.add(exchange.getRequestURI().getPath());
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(201, -1);
        exchange.close();
    }

    private static void handleRegistry(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        registryRequests.add(path);
        exchange.getRequestBody().readAllBytes();
        if (path.startsWith("/config/") && "PUT".equals(exchange.getRequestMethod())) {
            byte[] body = "{\"compatibility\":\"BACKWARD\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        if (path.startsWith("/subjects/") && path.endsWith("/versions") && "POST".equals(exchange.getRequestMethod())) {
            byte[] body = "{\"id\":101,\"version\":7}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate free port for AsyncAPI test stub", e);
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
