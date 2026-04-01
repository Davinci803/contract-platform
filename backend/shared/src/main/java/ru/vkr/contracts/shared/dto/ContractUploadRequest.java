package ru.vkr.contracts.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.vkr.contracts.shared.model.ContractType;

public record ContractUploadRequest(
        @NotBlank String name,
        @NotNull ContractType type,
        @NotBlank String content,
        String author
) {
}
