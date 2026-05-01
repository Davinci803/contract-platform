package ru.vkr.contracts.worker.generation.asyncapi;

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

class AsyncApiPipelineTest {
    private HttpServer nexusServer;
    private HttpServer schemaRegistryServer;

    @AfterEach
    void shutdownServers() {
        if (nexusServer != null) {
            nexusServer.stop(0);
        }
        if (schemaRegistryServer != null) {
            schemaRegistryServer.stop(0);
        }
    }

    @Test
    void shouldGenerateRegisterSchemaAndPublishForValidAsyncApi() throws IOException {
        Set<String> uploadedPaths = ConcurrentHashMap.newKeySet();
        Set<String> schemaRequests = ConcurrentHashMap.newKeySet();
        startNexusServer(uploadedPaths);
        startSchemaRegistryServer(schemaRequests, false);

        AsyncApiPipeline pipeline = new AsyncApiPipeline(
                "ru.vkr.contracts.generated",
                "kafka-lib",
                "value",
                "http://localhost:" + nexusServer.getAddress().getPort(),
                "maven-releases",
                "",
                "",
                "http://localhost:" + schemaRegistryServer.getAddress().getPort(),
                "BACKWARD"
        );

        GenerationResult result = pipeline.generateAndPublish(
                "Payments Stream",
                "1.3.0",
                validAsyncApi("PaymentCreated")
        );

        assertEquals("ru.vkr.contracts.generated:payments-stream-kafka-lib:1.3.0", result.coordinates());
        assertTrue(result.publicationUrl().contains("/payments-stream-kafka-lib/1.3.0/"));
        assertNotNull(result.schemaSubject());
        assertTrue(result.schemaSubject().contains("payments.stream"));
        assertTrue(uploadedPaths.stream().anyMatch(path -> path.endsWith(".jar")));
        assertTrue(uploadedPaths.stream().anyMatch(path -> path.endsWith(".pom")));
        assertTrue(schemaRequests.stream().anyMatch(path -> path.contains("/config/")));
        assertTrue(schemaRequests.stream().anyMatch(path -> path.contains("/subjects/")));
        assertTrue(result.log().contains("[schema-registry]"));
        assertTrue(result.log().contains("[publish]"));
    }

    @Test
    void shouldRejectInvalidAsyncApi() {
        AsyncApiPipeline pipeline = new AsyncApiPipeline(
                "ru.vkr.contracts.generated",
                "kafka-lib",
                "value",
                "http://localhost:8081",
                "maven-releases",
                "",
                "",
                "http://localhost:8085",
                "BACKWARD"
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.generateAndPublish("Broken Async", "1.0.0", "asyncapi: 2.6.0")
        );
        assertTrue(error.getMessage().contains("channels"));
    }

    @Test
    void shouldFailWhenSchemaCompatibilityConflictOccurs() throws IOException {
        Set<String> schemaRequests = ConcurrentHashMap.newKeySet();
        startSchemaRegistryServer(schemaRequests, true);

        AsyncApiPipeline pipeline = new AsyncApiPipeline(
                "ru.vkr.contracts.generated",
                "kafka-lib",
                "value",
                "http://127.0.0.1:1",
                "maven-releases",
                "",
                "",
                "http://localhost:" + schemaRegistryServer.getAddress().getPort(),
                "FULL"
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> pipeline.generateAndPublish("Payments Stream", "1.0.0", validAsyncApi("PaymentCreated"))
        );
        assertTrue(error.getMessage().contains("compatibility conflict"));
        assertTrue(schemaRequests.stream().anyMatch(path -> path.contains("/subjects/")));
    }

    @Test
    void shouldRollbackRegisteredSchemaWhenNexusPublishingFails() throws IOException {
        Set<String> schemaRequests = ConcurrentHashMap.newKeySet();
        startSchemaRegistryServer(schemaRequests, false);

        AsyncApiPipeline pipeline = new AsyncApiPipeline(
                "ru.vkr.contracts.generated",
                "kafka-lib",
                "value",
                "http://127.0.0.1:1",
                "maven-releases",
                "",
                "",
                "http://localhost:" + schemaRegistryServer.getAddress().getPort(),
                "BACKWARD"
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> pipeline.generateAndPublish("Payments Stream", "2.0.0", validAsyncApi("PaymentCreated"))
        );
        assertTrue(error.getMessage().contains("Nexus upload"));
        assertTrue(schemaRequests.stream().anyMatch(path -> path.contains("/versions/3")));
    }

    private void startNexusServer(Set<String> uploadedPaths) throws IOException {
        nexusServer = HttpServer.create(new InetSocketAddress(0), 0);
        nexusServer.createContext("/", exchange -> handleNexusUpload(uploadedPaths, exchange));
        nexusServer.start();
    }

    private void startSchemaRegistryServer(Set<String> requests, boolean returnConflictOnRegister) throws IOException {
        schemaRegistryServer = HttpServer.create(new InetSocketAddress(0), 0);
        schemaRegistryServer.createContext("/", exchange -> handleSchemaRegistry(requests, returnConflictOnRegister, exchange));
        schemaRegistryServer.start();
    }

    private void handleNexusUpload(Set<String> uploadedPaths, HttpExchange exchange) throws IOException {
        uploadedPaths.add(exchange.getRequestURI().getPath());
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(201, -1);
        exchange.close();
    }

    private void handleSchemaRegistry(Set<String> requests, boolean returnConflictOnRegister, HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(path);
        exchange.getRequestBody().readAllBytes();
        if (path.startsWith("/config/") && "PUT".equals(exchange.getRequestMethod())) {
            byte[] body = "{\"compatibility\":\"BACKWARD\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        if (path.contains("/versions") && "POST".equals(exchange.getRequestMethod())) {
            if (returnConflictOnRegister) {
                byte[] body = "{\"error_code\":409,\"message\":\"Schema being registered is incompatible\"}".getBytes();
                exchange.sendResponseHeaders(409, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }
            byte[] body = "{\"id\":17,\"version\":3}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        if (path.contains("/versions/") && "DELETE".equals(exchange.getRequestMethod())) {
            byte[] body = "[3]".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    private String validAsyncApi(String messageName) {
        return """
                asyncapi: 2.6.0
                info:
                  title: Payment Events
                  version: 1.0.0
                channels:
                  payment/created:
                    publish:
                      message:
                        name: %s
                        payload:
                          type: object
                          properties:
                            eventId:
                              type: string
                            amount:
                              type: number
                """.formatted(messageName);
    }
}
