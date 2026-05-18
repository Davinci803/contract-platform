package ru.vkr.contracts.shared.dto;

import jakarta.validation.constraints.NotNull;

public record GenerateRequest(@NotNull Long contractVersionId, Boolean publishInNewMajorSubject) {
    public boolean publishInNewMajorSubjectEnabled() {
        return Boolean.TRUE.equals(publishInNewMajorSubject);
    }
}
