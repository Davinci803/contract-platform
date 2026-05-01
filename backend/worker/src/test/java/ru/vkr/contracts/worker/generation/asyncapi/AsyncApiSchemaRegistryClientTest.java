package ru.vkr.contracts.worker.generation.asyncapi;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncApiSchemaRegistryClientTest {
    private HttpServer server;

    @AfterEach
    void shutdownServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldConvertPayloadToAvroRecordSchema() {
        AsyncApiSchemaRegistryClient client = new AsyncApiSchemaRegistryClient(
                "http://localhost:8085",
                "",
                "",
                "BACKWARD",
                HttpClient.newBuilder().build()
        );
        Map<String, Object> payload = Map.of(
                "type", "object",
                "properties", Map.of(
                        "eventId", Map.of("type", "string"),
                        "amount", Map.of("type", "number")
                ),
                "required", java.util.List.of("eventId")
        );

        String avroSchema = client.toCanonicalSchema(payload, "AVRO", "payments.events.created");

        assertTrue(avroSchema.contains("\"type\":\"record\""));
        assertTrue(avroSchema.contains("\"name\":\"PaymentsEventsCreated\""));
        assertTrue(avroSchema.contains("\"name\":\"eventId\""));
        assertTrue(avroSchema.contains("\"name\":\"amount\""));
    }

    @Test
    void shouldSendSchemaTypeInRegisterRequest() throws IOException {
        AtomicReference<String> registerBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/config/sample-subject", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"compatibility\":\"BACKWARD\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/subjects/sample-subject/versions", exchange -> {
            registerBody.set(new String(exchange.getRequestBody().readAllBytes()));
            byte[] body = "{\"id\":13,\"version\":2}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        AsyncApiSchemaRegistryClient client = new AsyncApiSchemaRegistryClient(
                "http://localhost:" + server.getAddress().getPort(),
                "",
                "",
                "BACKWARD",
                HttpClient.newBuilder().build()
        );
        AsyncApiSchemaRegistryClient.SchemaRegistration registration = client.register(
                "sample-subject",
                "{\"type\":\"record\",\"name\":\"Event\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}",
                "AVRO",
                new StringBuilder()
        );

        assertEquals(13, registration.id());
        assertEquals(2, registration.version());
        assertTrue(registerBody.get().contains("\"schemaType\":\"AVRO\""));
    }

    @Test
    void shouldResolveVersionFromLatestWhenRegisterResponseContainsOnlyId() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/config/sample-subject", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"compatibility\":\"BACKWARD\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/subjects/sample-subject/versions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"id\":2}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/subjects/sample-subject/versions/latest", exchange -> {
            byte[] body = "{\"subject\":\"sample-subject\",\"version\":5,\"id\":2}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        AsyncApiSchemaRegistryClient client = new AsyncApiSchemaRegistryClient(
                "http://localhost:" + server.getAddress().getPort(),
                "",
                "",
                "BACKWARD",
                HttpClient.newBuilder().build()
        );
        AsyncApiSchemaRegistryClient.SchemaRegistration registration = client.register(
                "sample-subject",
                "{\"type\":\"record\",\"name\":\"Event\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}",
                "AVRO",
                new StringBuilder()
        );

        assertEquals(2, registration.id());
        assertEquals(5, registration.version());
    }
}
