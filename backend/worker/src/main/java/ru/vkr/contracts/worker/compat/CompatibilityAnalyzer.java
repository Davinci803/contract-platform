package ru.vkr.contracts.worker.compat;

import org.springframework.stereotype.Component;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.shared.model.CompatibilityLevel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CompatibilityAnalyzer {
    private static final String INITIAL_VERSION_CODE = "INITIAL_VERSION";
    private static final String NO_CHANGES_CODE = "NO_CHANGES";
    private final Map<ContractType, CompatibilityRulesEngine> rulesEnginesByType;
    private final SemverRecommendationPolicy semverRecommendationPolicy;

    public CompatibilityAnalyzer(List<CompatibilityRulesEngine> rulesEngines, SemverRecommendationPolicy semverRecommendationPolicy) {
        this.rulesEnginesByType = rulesEngines.stream()
                .collect(Collectors.toMap(CompatibilityRulesEngine::supportedType, Function.identity()));
        this.semverRecommendationPolicy = semverRecommendationPolicy;
    }

    public CompatibilityResult analyze(String previousSpec, String newSpec) {
        ContractType guessedType = (newSpec != null && newSpec.contains("asyncapi")) ? ContractType.ASYNCAPI : ContractType.OPENAPI;
        return analyze(previousSpec, newSpec, guessedType);
    }

    public CompatibilityResult analyze(String previousSpec, String newSpec, ContractType type) {
        if (previousSpec == null || previousSpec.isBlank()) {
            return new CompatibilityResult(
                    CompatibilityLevel.COMPATIBLE,
                    "MINOR",
                    List.of(new CompatibilityFinding(
                            INITIAL_VERSION_CODE,
                            ChangeSeverity.ADVISORY,
                            false,
                            "contract",
                            "Initial version published, no backward-compatibility baseline"
                    ))
            );
        }

        CompatibilityRulesEngine engine = rulesEnginesByType.get(type);
        if (engine == null) {
            throw new IllegalArgumentException("Unsupported contract type for compatibility analysis: " + type);
        }
        List<CompatibilityFinding> findings = engine.analyze(previousSpec, newSpec);

        if (findings.isEmpty()) {
            findings.add(new CompatibilityFinding(
                    NO_CHANGES_CODE,
                    ChangeSeverity.ADVISORY,
                    false,
                    "contract",
                    "No contract changes detected"
            ));
        }

        boolean hasBreaking = findings.stream().anyMatch(CompatibilityFinding::breaking);
        String semver = semverRecommendationPolicy.recommend(findings);
        return new CompatibilityResult(
                hasBreaking ? CompatibilityLevel.BREAKING : CompatibilityLevel.COMPATIBLE,
                semver,
                List.copyOf(findings)
        );
    }
}
