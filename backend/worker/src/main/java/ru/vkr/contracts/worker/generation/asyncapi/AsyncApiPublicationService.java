package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.vkr.contracts.worker.generation.PermanentGenerationException;
import ru.vkr.contracts.worker.generation.TransientGenerationException;

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
            CompensationOutcome compensationOutcome = maybeRollbackSchema(schemaSubject, schemaRegistration, log);
            String details = "AsyncAPI publication failed; compensation=" + compensationOutcome.label + "; cause=" + e.getMessage();
            if (e instanceof TransientGenerationException) {
                throw new TransientGenerationException(details, e);
            }
            if (e instanceof PermanentGenerationException) {
                throw new PermanentGenerationException(details, e);
            }
            throw new PermanentGenerationException(details, e);
        }
    }

    private CompensationOutcome maybeRollbackSchema(
            String schemaSubject,
            AsyncApiSchemaRegistryClient.SchemaRegistration schemaRegistration,
            StringBuilder log
    ) {
        if (schemaSubject == null || schemaRegistration == null) {
            appendStage(log, "compensation", "rollback skipped (no schema registration)");
            return CompensationOutcome.NOT_REQUIRED;
        }
        try {
            appendStage(log, "schema-registry", "rolling back schema registration");
            schemaRegistryClient.deleteSubjectVersion(schemaSubject, schemaRegistration.version(), log);
            appendStage(log, "compensation", "rollback completed for subject=" + schemaSubject + " version=" + schemaRegistration.version());
            return CompensationOutcome.ROLLBACK_OK;
        } catch (RuntimeException rollbackFailure) {
            appendStage(log, "compensation", "rollback failed: " + rollbackFailure.getMessage());
            return CompensationOutcome.ROLLBACK_FAILED;
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

    private enum CompensationOutcome {
        NOT_REQUIRED("not_required"),
        ROLLBACK_OK("rollback_ok"),
        ROLLBACK_FAILED("rollback_failed");

        private final String label;

        CompensationOutcome(String label) {
            this.label = label;
        }
    }
}
