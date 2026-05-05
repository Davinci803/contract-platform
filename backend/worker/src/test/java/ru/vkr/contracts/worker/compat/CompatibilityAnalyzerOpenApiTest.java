package ru.vkr.contracts.worker.compat;

import org.junit.jupiter.api.Test;
import ru.vkr.contracts.shared.model.CompatibilityLevel;
import ru.vkr.contracts.shared.model.ContractType;

import java.util.List;
import java.util.Set;

import static ru.vkr.contracts.worker.compat.CompatibilityAnalyzerTestFixtures.assertScenarioMatrix;
import static ru.vkr.contracts.worker.compat.CompatibilityAnalyzerTestFixtures.mapOf;
import static ru.vkr.contracts.worker.compat.CompatibilityAnalyzerTestFixtures.openApiSpec;

class CompatibilityAnalyzerOpenApiTest {
    @Test
    void regressionMatrixShouldClassifyExpectedly() {
        List<CompatibilityAnalyzerTestFixtures.Scenario> scenarios = List.of(
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "initial-version",
                        null,
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "INITIAL_VERSION"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "endpoint-added",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/payments", "/refunds"), mapOf("id", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "OPENAPI_ENDPOINT_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "endpoint-removed",
                        openApiSpec(List.of("/payments", "/refunds"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "OPENAPI_ENDPOINT_REMOVED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "schema-removed",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), true),
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "OPENAPI_SCHEMA_REMOVED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "required-field-added",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/payments"), mapOf("id", "string", "status", "string"), Set.of("id", "status"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "OPENAPI_REQUIRED_FIELD_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "optional-field-added",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/payments"), mapOf("id", "string", "status", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "OPENAPI_OPTIONAL_FIELD_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "field-removed",
                        openApiSpec(List.of("/payments"), mapOf("id", "string", "amount", "number"), Set.of("id"), false),
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "OPENAPI_FIELD_REMOVED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "field-type-changed",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/payments"), mapOf("id", "integer"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "OPENAPI_FIELD_TYPE_CHANGED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "no-changes",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "PATCH",
                        "NO_CHANGES"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "mixed-break-and-minor",
                        openApiSpec(List.of("/payments"), mapOf("id", "string"), Set.of("id"), false),
                        openApiSpec(List.of("/refunds"), mapOf("id", "string", "status", "string"), Set.of("id"), false),
                        ContractType.OPENAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "OPENAPI_ENDPOINT_REMOVED"
                )
        );

        assertScenarioMatrix(scenarios);
    }
}
