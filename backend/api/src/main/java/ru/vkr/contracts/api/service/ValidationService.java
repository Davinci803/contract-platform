package ru.vkr.contracts.api.service;

import org.springframework.stereotype.Service;
import ru.vkr.contracts.shared.model.ContractType;

@Service
public class ValidationService {

    public void validateByType(ContractType type, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Specification content is empty");
        }
        switch (type) {
            case OPENAPI -> validateOpenApi(content);
            case ASYNCAPI -> validateAsyncApi(content);
        }
    }

    private void validateOpenApi(String content) {
        if (!(content.contains("openapi:") || content.contains("\"openapi\""))) {
            throw new IllegalArgumentException("OpenAPI signature is missing");
        }
        if (!content.contains("paths")) {
            throw new IllegalArgumentException("OpenAPI paths section is missing");
        }
    }

    private void validateAsyncApi(String content) {
        if (!(content.contains("asyncapi:") || content.contains("\"asyncapi\""))) {
            throw new IllegalArgumentException("AsyncAPI signature is missing");
        }
        if (!content.contains("channels")) {
            throw new IllegalArgumentException("AsyncAPI channels section is missing");
        }
    }
}
