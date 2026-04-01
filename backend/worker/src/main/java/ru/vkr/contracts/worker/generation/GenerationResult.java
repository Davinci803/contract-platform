package ru.vkr.contracts.worker.generation;

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
