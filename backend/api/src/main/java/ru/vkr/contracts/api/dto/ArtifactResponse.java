package ru.vkr.contracts.api.dto;

public record ArtifactResponse(
        Long id,
        Long jobId,
        String coordinates,
        String publicationUrl,
        String schemaSubject
) {
}
