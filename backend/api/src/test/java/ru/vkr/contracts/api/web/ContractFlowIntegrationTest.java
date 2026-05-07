package ru.vkr.contracts.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContractFlowIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldUploadContractVersion() {
        ResponseEntity<Map> response = uploadContractVersion("Payments Upload", "OPENAPI", loadFixture("specs/openapi/payment-api-v1.yaml"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("contractVersionId"));
    }

    @Test
    void shouldReturnContractHistoryWithNewestVersionFirst() {
        ResponseEntity<Map> firstUpload = uploadContractVersion("Payments History", "OPENAPI", loadFixture("specs/openapi/payment-api-v1.yaml"));
        ResponseEntity<Map> secondUpload = uploadContractVersion("Payments History", "OPENAPI", loadFixture("specs/openapi/payment-api-v2.yaml"));

        Long contractId = ((Number) secondUpload.getBody().get("contractId")).longValue();
        ResponseEntity<List> historyResponse = restTemplate
                .withBasicAuth("developer", "dev123")
                .getForEntity("http://localhost:" + port + "/api/contracts/" + contractId + "/versions", List.class);

        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        assertNotNull(historyResponse.getBody());
        assertEquals(2, historyResponse.getBody().size());

        Map firstHistoryEntry = (Map) historyResponse.getBody().get(0);
        Map secondHistoryEntry = (Map) historyResponse.getBody().get(1);
        assertEquals("1.1.0", firstHistoryEntry.get("version"));
        assertEquals("1.0.0", secondHistoryEntry.get("version"));
        assertNotNull(firstUpload.getBody().get("contractVersionId"));
    }

    @Test
    void shouldCreateGenerationJobAndFetchJobStatus() {
        ResponseEntity<Map> uploadResponse = uploadContractVersion("Payments Job Status", "OPENAPI", loadFixture("specs/openapi/payment-api-v1.yaml"));
        Long contractVersionId = ((Number) uploadResponse.getBody().get("contractVersionId")).longValue();

        HttpEntity<Map<String, Object>> createJobRequest = jsonRequest(Map.of("contractVersionId", contractVersionId));
        ResponseEntity<Map> createJobResponse = restTemplate
                .withBasicAuth("developer", "dev123")
                .postForEntity("http://localhost:" + port + "/api/generation-jobs", createJobRequest, Map.class);

        assertEquals(HttpStatus.ACCEPTED, createJobResponse.getStatusCode());
        assertNotNull(createJobResponse.getBody());
        assertNotNull(createJobResponse.getBody().get("jobId"));

        Long jobId = ((Number) createJobResponse.getBody().get("jobId")).longValue();
        ResponseEntity<Map> statusResponse = restTemplate
                .withBasicAuth("developer", "dev123")
                .getForEntity("http://localhost:" + port + "/api/generation-jobs/" + jobId, Map.class);

        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertNotNull(statusResponse.getBody());
        assertEquals(jobId.intValue(), statusResponse.getBody().get("jobId"));
        assertTrue(statusResponse.getBody().containsKey("status"));
    }

    @Test
    void shouldRejectUploadWhenValidationFails() {
        ResponseEntity<Map> response = uploadContractVersion(" ", "OPENAPI", loadFixture("specs/openapi/payment-api-v1.yaml"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(String.valueOf(response.getBody().get("error")).contains("name"));
    }

    @Test
    void shouldReturnBadRequestForMissingContractHistory() {
        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("developer", "dev123")
                .getForEntity("http://localhost:" + port + "/api/contracts/999999/versions", Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(String.valueOf(response.getBody().get("error")).contains("Contract not found"));
    }

    @Test
    void shouldReturnBadRequestWhenCreatingGenerationJobForMissingVersion() {
        HttpEntity<Map<String, Object>> createJobRequest = jsonRequest(Map.of("contractVersionId", 999999L));
        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("developer", "dev123")
                .postForEntity("http://localhost:" + port + "/api/generation-jobs", createJobRequest, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(String.valueOf(response.getBody().get("error")).contains("Contract version not found"));
    }

    @Test
    void shouldReturnBadRequestForMissingGenerationJob() {
        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("developer", "dev123")
                .getForEntity("http://localhost:" + port + "/api/generation-jobs/999999", Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(String.valueOf(response.getBody().get("error")).contains("Job not found"));
    }

    private ResponseEntity<Map> uploadContractVersion(String name, String type, String content) {
        HttpEntity<Map<String, Object>> request = jsonRequest(Map.of(
                "name", name,
                "type", type,
                "content", content,
                "author", "tester"
        ));
        return restTemplate
                .withBasicAuth("developer", "dev123")
                .postForEntity("http://localhost:" + port + "/api/contracts/versions", request, Map.class);
    }

    private HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String loadFixture(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load test fixture: " + classpathLocation, e);
        }
    }
}
