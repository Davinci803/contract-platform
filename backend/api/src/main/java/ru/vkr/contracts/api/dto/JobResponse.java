package ru.vkr.contracts.api.dto;

import ru.vkr.contracts.shared.model.JobStatus;

public record JobResponse(
        Long jobId,
        Long contractVersionId,
        String correlationId,
        JobStatus status,
        String log
) {
}
