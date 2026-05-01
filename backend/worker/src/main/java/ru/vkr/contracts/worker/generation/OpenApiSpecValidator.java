package ru.vkr.contracts.worker.generation;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenApiSpecValidator {

    public OpenAPI parseAndValidate(String content, String label, StringBuilder log) {
        appendStage(log, "validation", "parsing " + label + " spec");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("OpenAPI content is empty");
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        SwaggerParseResult parseResult = new OpenAPIParser().readContents(content, null, options);
        OpenAPI openAPI = parseResult == null ? null : parseResult.getOpenAPI();
        List<String> messages = parseResult == null ? List.of("Parser returned no result") : parseResult.getMessages();
        if (openAPI == null) {
            throw new IllegalArgumentException("OpenAPI parse failed: " + String.join("; ", messages));
        }
        if (messages != null && !messages.isEmpty()) {
            throw new IllegalArgumentException("OpenAPI validation errors: " + String.join("; ", messages));
        }
        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            throw new IllegalArgumentException("OpenAPI paths section is empty");
        }
        appendStage(log, "validation", "parsed successfully, paths=" + openAPI.getPaths().size());
        return openAPI;
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }
}
