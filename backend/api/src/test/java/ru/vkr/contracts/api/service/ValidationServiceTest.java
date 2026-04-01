package ru.vkr.contracts.api.service;

import org.junit.jupiter.api.Test;
import ru.vkr.contracts.shared.model.ContractType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationServiceTest {
    private final ValidationService validationService = new ValidationService();

    @Test
    void shouldValidateOpenApi() {
        String content = "openapi: 3.0.1\npaths:\n  /api/test:\n    get: {}";
        assertDoesNotThrow(() -> validationService.validateByType(ContractType.OPENAPI, content));
    }

    @Test
    void shouldRejectInvalidAsyncApi() {
        String content = "asyncapi: 2.6.0\ninfo:\n  title: test";
        assertThrows(IllegalArgumentException.class, () -> validationService.validateByType(ContractType.ASYNCAPI, content));
    }
}
