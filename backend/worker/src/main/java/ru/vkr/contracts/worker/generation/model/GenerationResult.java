package ru.vkr.contracts.worker.generation.model;

public record GenerationResult(
        String groupId,
        String artifactId,
        String version,
        String coordinates,
        String publicationUrl,
        String schemaSubject,
        String log
) {
}
