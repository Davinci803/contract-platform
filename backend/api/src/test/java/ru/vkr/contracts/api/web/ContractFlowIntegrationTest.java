package ru.vkr.contracts.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContractFlowIntegrationTest {
    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldUploadContractVersion() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of(
                "name", "Payment API",
                "type", "OPENAPI",
                "content", """
                        openapi: 3.0.1
                        info:
                          title: Payment API
                          version: 1.0.0
                        paths:
                          /api/pay:
                            get:
                              responses:
                                "200":
                                  description: ok
                        """,
                "author", "tester"
        ), headers);

        ResponseEntity<Map> response = restTemplate
                .withBasicAuth("developer", "dev123")
                .postForEntity("http://localhost:" + port + "/api/contracts/versions", request, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("contractVersionId"));
    }
}
