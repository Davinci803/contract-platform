package ru.vkr.contracts.worker.compat;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SemverRecommendationPolicy {
    public String recommend(List<CompatibilityFinding> findings) {
        if (findings.stream().anyMatch(f -> f.severity() == ChangeSeverity.CRITICAL || f.severity() == ChangeSeverity.MAJOR)) {
            return "MAJOR";
        }
        if (findings.stream().anyMatch(f -> f.severity() == ChangeSeverity.MINOR)) {
            return "MINOR";
        }
        return "PATCH";
    }
}
