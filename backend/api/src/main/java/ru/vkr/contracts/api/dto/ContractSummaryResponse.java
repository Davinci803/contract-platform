package ru.vkr.contracts.api.dto;

import ru.vkr.contracts.shared.model.ContractType;

public record ContractSummaryResponse(
        Long contractId,
        String contractName,
        ContractType contractType
) {
}
