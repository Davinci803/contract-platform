package ru.vkr.contracts.worker.compat;

import ru.vkr.contracts.shared.model.CompatibilityLevel;

import java.util.List;

public record CompatibilityResult(
        CompatibilityLevel level,
        String recommendedSemverIncrement,
        List<String> findings
) {
}
