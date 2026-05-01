package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AsyncApiNamingStrategy {
    private final String artifactSuffix;
    private final String subjectSuffix;

    public AsyncApiNamingStrategy(
            @Value("${generation.async-api.artifact-suffix:kafka-lib}") String artifactSuffix,
            @Value("${generation.async-api.schema-registry.subject-suffix:value}") String subjectSuffix
    ) {
        this.artifactSuffix = artifactSuffix;
        this.subjectSuffix = subjectSuffix;
    }

    public String toArtifactId(String contractName) {
        String normalized = contractName == null
                ? "contract"
                : contractName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String compact = normalized.replaceAll("(^-+|-+$)", "");
        if (compact.isBlank()) {
            compact = "contract";
        }
        return compact + "-" + artifactSuffix;
    }

    public String buildSubject(String contractName, String messageName) {
        return normalizeForSubject(contractName)
                + "."
                + normalizeForSubject(messageName)
                + "-"
                + normalizeForSubject(subjectSuffix);
    }

    private String normalizeForSubject(String value) {
        if (value == null || value.isBlank()) {
            return "event";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
        String compact = normalized.replaceAll("(^\\.+|\\.+$)", "");
        return compact.isBlank() ? "event" : compact;
    }
}
