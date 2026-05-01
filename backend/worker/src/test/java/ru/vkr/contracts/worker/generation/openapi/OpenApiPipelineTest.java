package ru.vkr.contracts.worker.generation.openapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.vkr.contracts.worker.generation.model.GenerationResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiPipelineTest {
    private HttpServer server;

    @AfterEach
    void shutdownServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldGenerateBuildAndPublishForValidOpenApi() throws IOException {
        Set<String> uploadedPaths = ConcurrentHashMap.newKeySet();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handleUpload(uploadedPaths, exchange));
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        OpenApiPipeline pipeline = new OpenApiPipeline(
                "ru.vkr.contracts.generated",
                "rest-client",
                baseUrl,
                "maven-releases",
                "",
                "",
                new OpenApiDiffTool()
        );

        GenerationResult result = pipeline.generateAndPublish(
                "Payment API",
                "1.2.0",
                validOpenApi("Payment API", "/payments"),
                validOpenApi("Payment API", "/payments-v1")
        );

        assertEquals("ru.vkr.contracts.generated:payment-api-rest-client:1.2.0", result.coordinates());
        assertTrue(result.publicationUrl().endsWith("payment-api-rest-client/1.2.0/payment-api-rest-client-1.2.0.jar"));
        assertTrue(uploadedPaths.stream().anyMatch(path -> path.endsWith(".jar")));
        assertTrue(uploadedPaths.stream().anyMatch(path -> path.endsWith(".pom")));
        assertTrue(result.log().contains("[validation]"));
        assertTrue(result.log().contains("[diff]"));
        assertTrue(result.log().contains("[generation]"));
        assertTrue(result.log().contains("[build]"));
        assertTrue(result.log().contains("[publish]"));
    }

    @Test
    void shouldRejectInvalidOpenApi() {
        OpenApiPipeline pipeline = new OpenApiPipeline(
                "ru.vkr.contracts.generated",
                "rest-client",
                "http://localhost:8081",
                "maven-releases",
                "",
                "",
                new OpenApiDiffTool()
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.generateAndPublish("Broken API", "1.0.0", "openapi: bad", null)
        );
        assertNotNull(error.getMessage());
    }

    @Test
    void shouldFailWhenNexusIsUnavailable() {
        OpenApiPipeline pipeline = new OpenApiPipeline(
                "ru.vkr.contracts.generated",
                "rest-client",
                "http://127.0.0.1:1",
                "maven-releases",
                "",
                "",
                new OpenApiDiffTool()
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> pipeline.generateAndPublish("Payment API", "1.0.0", validOpenApi("Payment API", "/payments"), null)
        );
        assertTrue(error.getMessage().contains("Nexus upload"));
    }

    private void handleUpload(Set<String> uploadedPaths, HttpExchange exchange) throws IOException {
        uploadedPaths.add(exchange.getRequestURI().getPath());
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(201, -1);
        exchange.close();
    }

    private String validOpenApi(String title, String path) {
        return """
                openapi: 3.0.1
                info:
                  title: %s
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
                """.formatted(title, path);
    }
}
