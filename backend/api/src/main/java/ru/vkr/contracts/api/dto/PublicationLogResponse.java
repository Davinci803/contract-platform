package ru.vkr.contracts.api.dto;

public record PublicationLogResponse(
        Long id,
        Long jobId,
        String target,
        String status,
        String message,
        String eventType,
        String errorCategory,
        String correlationId
) {
}
