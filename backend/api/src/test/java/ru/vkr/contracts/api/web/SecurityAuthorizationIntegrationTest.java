package ru.vkr.contracts.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityAuthorizationIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void viewerShouldNotUploadContractVersion() {
        HttpEntity<Map<String, Object>> request = jsonRequest(Map.of(
                "name", "Viewer Upload",
                "type", "OPENAPI",
                "content", loadFixture("specs/openapi/payment-api-v1.yaml"),
                "author", "viewer"
        ));

        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("viewer", "view123")
                .postForEntity("http://localhost:" + port + "/api/contracts/versions", request, Map.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void viewerShouldReadContractHistory() {
        ResponseEntity<Map> uploadResponse = uploadAsDeveloper("Viewer Read", loadFixture("specs/openapi/payment-api-v1.yaml"));
        Long contractId = ((Number) uploadResponse.getBody().get("contractId")).longValue();

        ResponseEntity<List> historyResponse = restTemplate
                .withBasicAuth("viewer", "view123")
                .getForEntity("http://localhost:" + port + "/api/contracts/" + contractId + "/versions", List.class);

        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        assertNotNull(historyResponse.getBody());
    }

    @Test
    void anonymousShouldGetUnauthorizedForApi() {
        ResponseEntity<Map> response = restTemplate
                .getForEntity("http://localhost:" + port + "/api/read-model/summary", Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private ResponseEntity<Map> uploadAsDeveloper(String name, String content) {
        HttpEntity<Map<String, Object>> request = jsonRequest(Map.of(
                "name", name,
                "type", "OPENAPI",
                "content", content,
                "author", "developer"
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
