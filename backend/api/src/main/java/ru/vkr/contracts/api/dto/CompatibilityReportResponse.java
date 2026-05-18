package ru.vkr.contracts.api.dto;

import ru.vkr.contracts.shared.model.CompatibilityLevel;
import ru.vkr.contracts.shared.model.ContractType;

public record CompatibilityReportResponse(
        Long id,
        String contractName,
        String availableVersion,
        ContractType contractType,
        CompatibilityLevel level,
        String semverRecommendation,
        String findings,
        String migrationAdvice
) {
}
