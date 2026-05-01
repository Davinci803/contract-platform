package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

@Component
public class AsyncApiPublicationService {
    private final AsyncApiNexusPublisher nexusPublisher;
    private final AsyncApiSchemaRegistryClient schemaRegistryClient;
    private final String schemaType;

    public AsyncApiPublicationService(
            AsyncApiNexusPublisher nexusPublisher,
            AsyncApiSchemaRegistryClient schemaRegistryClient,
            @Value("${generation.async-api.schema-registry.schema-type:AVRO}") String schemaType
    ) {
        this.nexusPublisher = nexusPublisher;
        this.schemaRegistryClient = schemaRegistryClient;
        this.schemaType = normalizeSchemaType(schemaType);
    }

    public AsyncApiPublicationResult registerSchemaAndPublish(
            String groupId,
            String artifactId,
            String version,
            String schemaSubject,
            AsyncApiSpec spec,
            Path jarFile,
            Path pomFile,
            StringBuilder log
    ) {
        AsyncApiSchemaRegistryClient.SchemaRegistration schemaRegistration = null;
        try {
            String schemaJson = schemaRegistryClient.toCanonicalSchema(spec.primaryPayloadSchema(), schemaType, schemaSubject);
            schemaRegistration = schemaRegistryClient.register(schemaSubject, schemaJson, schemaType, log);
            appendStage(
                    log,
                    "schema-registry",
                    "registered subject=" + schemaSubject + ", schemaType=" + schemaType
                            + ", id=" + schemaRegistration.id() + ", version=" + schemaRegistration.version()
            );
            String publicationUrl = nexusPublisher.publish(groupId, artifactId, version, jarFile, pomFile, log);
            return new AsyncApiPublicationResult(publicationUrl, schemaSubject);
        } catch (RuntimeException e) {
            maybeRollbackSchema(schemaSubject, schemaRegistration, log);
            throw e;
        }
    }

    private void maybeRollbackSchema(
            String schemaSubject,
            AsyncApiSchemaRegistryClient.SchemaRegistration schemaRegistration,
            StringBuilder log
    ) {
        if (schemaSubject == null || schemaRegistration == null) {
            return;
        }
        try {
            appendStage(log, "schema-registry", "rolling back schema registration");
            schemaRegistryClient.deleteSubjectVersion(schemaSubject, schemaRegistration.version(), log);
        } catch (RuntimeException rollbackFailure) {
            appendStage(log, "schema-registry", "rollback failed: " + rollbackFailure.getMessage());
        }
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }

    private String normalizeSchemaType(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "AVRO";
        }
        String normalized = candidate.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AVRO", "JSON" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported schema registry schema-type: " + candidate);
        };
    }

    public record AsyncApiPublicationResult(
            String publicationUrl,
            String schemaSubject
    ) {
    }
}
