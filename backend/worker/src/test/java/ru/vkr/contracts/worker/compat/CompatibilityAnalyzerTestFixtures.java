package ru.vkr.contracts.worker.compat;

import ru.vkr.contracts.shared.model.CompatibilityLevel;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.worker.generation.asyncapi.AsyncApiSpecValidator;
import ru.vkr.contracts.worker.generation.openapi.OpenApiSpecValidator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompatibilityAnalyzerTestFixtures {
    static final CompatibilityAnalyzer ANALYZER = new CompatibilityAnalyzer(
            List.of(
                    new OpenApiCompatibilityRulesEngine(new OpenApiSpecValidator()),
                    new AsyncApiCompatibilityRulesEngine(new AsyncApiSpecValidator())
            ),
            new SemverRecommendationPolicy()
    );

    private CompatibilityAnalyzerTestFixtures() {
    }

    static void assertScenarioMatrix(List<Scenario> scenarios) {
        for (Scenario scenario : scenarios) {
            CompatibilityResult result = ANALYZER.analyze(scenario.previousSpec(), scenario.currentSpec(), scenario.type());
            assertEquals(scenario.expectedLevel(), result.level(), scenario.name());
            assertEquals(scenario.expectedSemver(), result.recommendedSemverIncrement(), scenario.name());
            assertTrue(
                    result.findings().stream().anyMatch(f -> scenario.expectedCode().equals(f.code())),
                    scenario.name() + " => expected code " + scenario.expectedCode() + ", but got " + result.findingsAsText()
            );
        }
    }

    static String openApiSpec(List<String> paths, Map<String, String> fields, Set<String> required, boolean includeAuditSchema) {
        StringBuilder content = new StringBuilder("""
                openapi: 3.0.1
                info:
                  title: Compatibility Test API
                  version: 1.0.0
                paths:
                """);
        for (String path : paths) {
            content.append("  ").append(path).append(":\n")
                    .append("    get:\n")
                    .append("      operationId: ").append(path.replace("/", "")).append("Get\n")
                    .append("      responses:\n")
                    .append("        \"200\":\n")
                    .append("          description: ok\n");
        }
        content.append("""
                components:
                  schemas:
                    Payment:
                      type: object
                      properties:
                """);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            content.append("        ").append(field.getKey()).append(":\n")
                    .append("          type: ").append(field.getValue()).append('\n');
        }
        if (!required.isEmpty()) {
            content.append("      required:\n");
            for (String field : required) {
                content.append("        - ").append(field).append('\n');
            }
        }
        if (includeAuditSchema) {
            content.append("    Audit:\n")
                    .append("      type: object\n")
                    .append("      properties:\n")
                    .append("        traceId:\n")
                    .append("          type: string\n");
        }
        return content.toString();
    }

    static String asyncApiSpec(List<AsyncMessage> messages, Map<String, String> payloadFields, Set<String> required) {
        StringBuilder content = new StringBuilder("""
                asyncapi: 2.6.0
                info:
                  title: Compatibility Test Events
                  version: 1.0.0
                channels:
                """);
        Map<String, List<AsyncMessage>> messagesByChannel = new LinkedHashMap<>();
        for (AsyncMessage message : messages) {
            messagesByChannel.computeIfAbsent(message.channel(), unused -> new java.util.ArrayList<>()).add(message);
        }
        for (Map.Entry<String, List<AsyncMessage>> channelEntry : messagesByChannel.entrySet()) {
            content.append("  ").append(channelEntry.getKey()).append(":\n");
            for (AsyncMessage message : channelEntry.getValue()) {
                content.append("    ").append(message.operation()).append(":\n")
                        .append("      message:\n")
                        .append("        name: ").append(message.name()).append('\n')
                        .append("        payload:\n")
                        .append("          type: object\n")
                        .append("          properties:\n");
                for (Map.Entry<String, String> field : payloadFields.entrySet()) {
                    content.append("            ").append(field.getKey()).append(":\n")
                            .append("              type: ").append(field.getValue()).append('\n');
                }
                if (!required.isEmpty()) {
                    content.append("          required:\n");
                    for (String field : required) {
                        content.append("            - ").append(field).append('\n');
                    }
                }
            }
        }
        return content.toString();
    }

    static Map<String, String> mapOf(String key, String value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    static Map<String, String> mapOf(String key1, String value1, String key2, String value2) {
        Map<String, String> result = mapOf(key1, value1);
        result.put(key2, value2);
        return result;
    }

    record Scenario(
            String name,
            String previousSpec,
            String currentSpec,
            ContractType type,
            CompatibilityLevel expectedLevel,
            String expectedSemver,
            String expectedCode
    ) {
    }

    record AsyncMessage(String channel, String operation, String name) {
    }
}
