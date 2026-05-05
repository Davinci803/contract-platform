package ru.vkr.contracts.worker.compat;

import ru.vkr.contracts.shared.model.ContractType;

import java.util.List;

interface CompatibilityRulesEngine {
    ContractType supportedType();

    List<CompatibilityFinding> analyze(String previousSpec, String currentSpec);
}
