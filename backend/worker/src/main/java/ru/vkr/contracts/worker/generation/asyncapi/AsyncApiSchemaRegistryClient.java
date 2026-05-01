package ru.vkr.contracts.worker.generation.asyncapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AsyncApiSchemaRegistryClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String compatibility;
    private final HttpClient httpClient;

    @Autowired
    public AsyncApiSchemaRegistryClient(
            @Value("${generation.async-api.schema-registry.base-url:http://localhost:8085}") String baseUrl,
            @Value("${generation.async-api.schema-registry.username:}") String username,
            @Value("${generation.async-api.schema-registry.password:}") String password,
            @Value("${generation.async-api.schema-registry.compatibility:BACKWARD}") String compatibility
    ) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.username = username;
        this.password = password;
        this.compatibility = compatibility == null || compatibility.isBlank() ? "BACKWARD" : compatibility;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public AsyncApiSchemaRegistryClient(
            String baseUrl,
            String username,
            String password,
            String compatibility,
            HttpClient httpClient
    ) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.username = username;
        this.password = password;
        this.compatibility = compatibility == null || compatibility.isBlank() ? "BACKWARD" : compatibility;
        this.httpClient = httpClient;
    }

    public SchemaRegistration register(String subject, String schemaJson, String schemaType, StringBuilder log) {
        String normalizedSchemaType = normalizeSchemaType(schemaType);
        appendStage(log, "schema-registry", "setting compatibility " + compatibility + " for " + subject);
        put(
                "/config/" + subject,
                toJson(Map.of("compatibility", compatibility)),
                "application/json"
        );
        appendStage(log, "schema-registry", "registering schema subject " + subject);
        String responseBody = post(
                "/subjects/" + subject + "/versions",
                toJson(Map.of("schemaType", normalizedSchemaType, "schema", schemaJson)),
                "application/vnd.schemaregistry.v1+json"
        );
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            int id = root.path("id").asInt(-1);
            int version = root.path("version").asInt(-1);
            if (id < 0) {
                throw new IllegalStateException("Schema Registry response missing id/version: " + trim(responseBody));
            }
            if (version < 0) {
                version = resolveVersion(subject, id, log);
            }
            if (version < 0) {
                throw new IllegalStateException("Schema Registry response missing id/version: " + trim(responseBody));
            }
            return new SchemaRegistration(id, version);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Schema Registry response parse failure: " + trim(responseBody), e);
        }
    }

    public void deleteSubjectVersion(String subject, int version, StringBuilder log) {
        appendStage(log, "schema-registry", "deleting subject version " + subject + "@" + version);
        HttpRequest request = baseRequest(baseUrl + "/subjects/" + subject + "/versions/" + version)
                .header("Accept", "application/json")
                .DELETE()
                .build();
        HttpResponse<String> response = send(request, "Schema Registry delete");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Schema Registry delete failed [" + response.statusCode() + "] for subject " + subject + ": " + trim(response.body())
            );
        }
    }

    public String toCanonicalSchema(Map<String, Object> payloadSchema, String schemaType, String subjectHint) {
        String normalizedSchemaType = normalizeSchemaType(schemaType);
        if ("JSON".equals(normalizedSchemaType)) {
            return toJson(payloadSchema);
        }
        return toJson(toAvroRecord(payloadSchema, toRecordName(subjectHint), toRecordName(subjectHint) + "Nested"));
    }

    private void put(String path, String body, String contentType) {
        HttpRequest request = baseRequest(baseUrl + path)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request, "Schema Registry compatibility update");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Schema Registry compatibility update failed [" + response.statusCode() + "]: " + trim(response.body())
            );
        }
    }

    private String post(String path, String body, String contentType) {
        HttpRequest request = baseRequest(baseUrl + path)
                .header("Content-Type", contentType)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request, "Schema Registry register");
        if (response.statusCode() == 409) {
            throw new IllegalStateException("Schema Registry compatibility conflict: " + trim(response.body()));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Schema Registry register failed [" + response.statusCode() + "]: " + trim(response.body()));
        }
        return response.body();
    }

    private int resolveVersion(String subject, int id, StringBuilder log) {
        appendStage(log, "schema-registry", "resolving schema version from latest endpoint");
        HttpRequest request = baseRequest(baseUrl + "/subjects/" + subject + "/versions/latest")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = send(request, "Schema Registry latest version lookup");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return -1;
        }
        try {
            JsonNode latest = OBJECT_MAPPER.readTree(response.body());
            int latestId = latest.path("id").asInt(-1);
            int latestVersion = latest.path("version").asInt(-1);
            if (latestVersion < 0) {
                return -1;
            }
            if (latestId == -1 || latestId == id) {
                return latestVersion;
            }
            return -1;
        } catch (JsonProcessingException e) {
            return -1;
        }
    }

    private HttpRequest.Builder baseRequest(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if (!username.isBlank()) {
            String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request, String operationLabel) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(operationLabel + " I/O failure: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operationLabel + " interrupted", e);
        }
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON payload", e);
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8085";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trim(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 240 ? compact.substring(0, 240) + "..." : compact;
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }

    public record SchemaRegistration(int id, int version) {
    }

    private String normalizeSchemaType(String schemaType) {
        if (schemaType == null || schemaType.isBlank()) {
            return "AVRO";
        }
        String normalized = schemaType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AVRO", "JSON" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported schema type: " + schemaType);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toAvroRecord(Map<String, Object> payloadSchema, String recordName, String nestedPrefix) {
        Object propertiesRaw = payloadSchema.get("properties");
        Map<String, Object> properties = propertiesRaw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Set<String> required = readRequired(payloadSchema.get("required"));

        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> propertySchema)) {
                continue;
            }
            String fieldName = sanitizeFieldName(entry.getKey());
            Object avroType = toAvroType((Map<String, Object>) propertySchema, nestedPrefix + toRecordName(entry.getKey()));
            if (!required.contains(entry.getKey())) {
                avroType = List.of("null", avroType);
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", fieldName);
            field.put("type", avroType);
            if (!required.contains(entry.getKey())) {
                field.put("default", null);
            }
            fields.add(field);
        }

        if (fields.isEmpty()) {
            fields.add(Map.of("name", "value", "type", "string", "default", ""));
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", "record");
        record.put("name", recordName);
        record.put("fields", fields);
        return record;
    }

    @SuppressWarnings("unchecked")
    private Object toAvroType(Map<String, Object> schema, String nestedRecordName) {
        String type = String.valueOf(schema.get("type")).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "string" -> "string";
            case "integer" -> "int";
            case "number" -> "double";
            case "boolean" -> "boolean";
            case "array" -> {
                Object itemsRaw = schema.get("items");
                Object itemsType = "string";
                if (itemsRaw instanceof Map<?, ?> itemsSchema) {
                    itemsType = toAvroType((Map<String, Object>) itemsSchema, nestedRecordName + "Item");
                }
                yield Map.of("type", "array", "items", itemsType);
            }
            case "object" -> toAvroRecord(schema, nestedRecordName, nestedRecordName + "Nested");
            default -> "string";
        };
    }

    @SuppressWarnings("unchecked")
    private Set<String> readRequired(Object requiredRaw) {
        if (!(requiredRaw instanceof List<?> requiredList)) {
            return Set.of();
        }
        return requiredList.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String toRecordName(String subjectHint) {
        if (subjectHint == null || subjectHint.isBlank()) {
            return "GeneratedEvent";
        }
        String[] parts = subjectHint.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        if (builder.isEmpty()) {
            builder.append("GeneratedEvent");
        }
        if (!Character.isLetter(builder.charAt(0)) && builder.charAt(0) != '_') {
            builder.insert(0, 'R');
        }
        return builder.toString();
    }

    private String sanitizeFieldName(String value) {
        if (value == null || value.isBlank()) {
            return "field";
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank()) {
            return "field";
        }
        if (!Character.isLetter(cleaned.charAt(0)) && cleaned.charAt(0) != '_') {
            cleaned = "f_" + cleaned;
        }
        return cleaned;
    }
}
