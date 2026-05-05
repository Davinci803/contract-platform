package ru.vkr.contracts.worker.compat;

import org.junit.jupiter.api.Test;
import ru.vkr.contracts.shared.model.CompatibilityLevel;
import ru.vkr.contracts.shared.model.ContractType;

import java.util.List;
import java.util.Set;

import static ru.vkr.contracts.worker.compat.CompatibilityAnalyzerTestFixtures.assertScenarioMatrix;
import static ru.vkr.contracts.worker.compat.CompatibilityAnalyzerTestFixtures.asyncApiSpec;
import static ru.vkr.contracts.worker.compat.CompatibilityAnalyzerTestFixtures.mapOf;

class CompatibilityAnalyzerAsyncApiTest {
    @Test
    void regressionMatrixShouldClassifyExpectedly() {
        List<CompatibilityAnalyzerTestFixtures.Scenario> scenarios = List.of(
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "initial-version",
                        null,
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "INITIAL_VERSION"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "channel-added",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated"),
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.refunded", "publish", "PaymentRefunded")
                                ),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "ASYNCAPI_CHANNEL_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "channel-removed",
                        asyncApiSpec(
                                List.of(
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated"),
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.refunded", "publish", "PaymentRefunded")
                                ),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "ASYNCAPI_CHANNEL_REMOVED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "message-added",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated"),
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "subscribe", "PaymentCreatedConsumer")
                                ),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "ASYNCAPI_MESSAGE_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "message-removed",
                        asyncApiSpec(
                                List.of(
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated"),
                                        new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "subscribe", "PaymentCreatedConsumer")
                                ),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "ASYNCAPI_MESSAGE_REMOVED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "required-field-added",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string", "status", "string"),
                                Set.of("id", "status")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "ASYNCAPI_REQUIRED_FIELD_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "optional-field-added",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string", "status", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "MINOR",
                        "ASYNCAPI_OPTIONAL_FIELD_ADDED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "field-removed",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string", "amount", "number"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "ASYNCAPI_FIELD_REMOVED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "field-type-changed",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "integer"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.BREAKING,
                        "MAJOR",
                        "ASYNCAPI_FIELD_TYPE_CHANGED"
                ),
                new CompatibilityAnalyzerTestFixtures.Scenario(
                        "no-changes",
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        asyncApiSpec(
                                List.of(new CompatibilityAnalyzerTestFixtures.AsyncMessage("payment.created", "publish", "PaymentCreated")),
                                mapOf("id", "string"),
                                Set.of("id")
                        ),
                        ContractType.ASYNCAPI,
                        CompatibilityLevel.COMPATIBLE,
                        "PATCH",
                        "NO_CHANGES"
                )
        );

        assertScenarioMatrix(scenarios);
    }
}
