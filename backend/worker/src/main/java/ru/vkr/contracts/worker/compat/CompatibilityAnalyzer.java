package ru.vkr.contracts.worker.compat;

import org.springframework.stereotype.Component;
import ru.vkr.contracts.shared.model.CompatibilityLevel;

import java.util.ArrayList;
import java.util.List;

@Component
public class CompatibilityAnalyzer {

    public CompatibilityResult analyze(String previousSpec, String newSpec) {
        if (previousSpec == null || previousSpec.isBlank()) {
            return new CompatibilityResult(CompatibilityLevel.COMPATIBLE, "MINOR", List.of("Initial version"));
        }
        List<String> findings = new ArrayList<>();
        CompatibilityLevel level = CompatibilityLevel.COMPATIBLE;
        String recommendedVersion = "MINOR";

        if (newSpec.contains("required: true") && !previousSpec.contains("required: true")) {
            level = CompatibilityLevel.BREAKING;
            recommendedVersion = "MAJOR";
            findings.add("New required fields detected");
        }
        if (previousSpec.contains("/api/") && !newSpec.contains("/api/")) {
            level = CompatibilityLevel.BREAKING;
            recommendedVersion = "MAJOR";
            findings.add("Potential endpoint removal detected");
        }
        if (findings.isEmpty()) {
            findings.add("Only backward-compatible changes detected");
        }
        return new CompatibilityResult(level, recommendedVersion, findings);
    }
}
