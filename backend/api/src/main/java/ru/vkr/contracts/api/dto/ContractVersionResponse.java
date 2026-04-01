package ru.vkr.contracts.api.dto;

import ru.vkr.contracts.shared.model.ContractType;

public record ContractVersionResponse(
        Long contractId,
        String contractName,
        ContractType contractType,
        Long contractVersionId,
        String version
) {
}
