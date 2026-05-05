package ru.vkr.contracts.worker.compat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.worker.generation.openapi.OpenApiSpecValidator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class OpenApiCompatibilityRulesEngine implements CompatibilityRulesEngine {
    private static final String OPENAPI_ENDPOINT_REMOVED = "OPENAPI_ENDPOINT_REMOVED";
    private static final String OPENAPI_ENDPOINT_ADDED = "OPENAPI_ENDPOINT_ADDED";
    private static final String OPENAPI_SCHEMA_REMOVED = "OPENAPI_SCHEMA_REMOVED";
    private static final String OPENAPI_SCHEMA_ADDED = "OPENAPI_SCHEMA_ADDED";
    private static final String OPENAPI_REQUIRED_FIELD_ADDED = "OPENAPI_REQUIRED_FIELD_ADDED";
    private static final String OPENAPI_OPTIONAL_FIELD_ADDED = "OPENAPI_OPTIONAL_FIELD_ADDED";
    private static final String OPENAPI_FIELD_REMOVED = "OPENAPI_FIELD_REMOVED";
    private static final String OPENAPI_FIELD_TYPE_CHANGED = "OPENAPI_FIELD_TYPE_CHANGED";

    private final OpenApiSpecValidator openApiSpecValidator;

    OpenApiCompatibilityRulesEngine(OpenApiSpecValidator openApiSpecValidator) {
        this.openApiSpecValidator = openApiSpecValidator;
    }

    @Override
    public ContractType supportedType() {
        return ContractType.OPENAPI;
    }

    @Override
    public List<CompatibilityFinding> analyze(String previousSpec, String currentSpec) {
        OpenAPI previous = openApiSpecValidator.parseAndValidate(previousSpec, "previous", new StringBuilder());
        OpenAPI current = openApiSpecValidator.parseAndValidate(currentSpec, "current", new StringBuilder());
        List<CompatibilityFinding> findings = new ArrayList<>();

        Set<String> previousEndpoints = collectOpenApiEndpoints(previous);
        Set<String> currentEndpoints = collectOpenApiEndpoints(current);

        for (String endpoint : difference(previousEndpoints, currentEndpoints)) {
            findings.add(new CompatibilityFinding(
                    OPENAPI_ENDPOINT_REMOVED,
                    ChangeSeverity.CRITICAL,
                    true,
                    endpoint,
                    "Endpoint removed from API surface"
            ));
        }
        for (String endpoint : difference(currentEndpoints, previousEndpoints)) {
            findings.add(new CompatibilityFinding(
                    OPENAPI_ENDPOINT_ADDED,
                    ChangeSeverity.MINOR,
                    false,
                    endpoint,
                    "New endpoint added"
            ));
        }

        Map<String, Schema<?>> previousSchemas = collectOpenApiSchemas(previous);
        Map<String, Schema<?>> currentSchemas = collectOpenApiSchemas(current);

        for (String schemaName : difference(previousSchemas.keySet(), currentSchemas.keySet())) {
            findings.add(new CompatibilityFinding(
                    OPENAPI_SCHEMA_REMOVED,
                    ChangeSeverity.MAJOR,
                    true,
                    "components.schemas." + schemaName,
                    "Schema removed from components"
            ));
        }
        for (String schemaName : difference(currentSchemas.keySet(), previousSchemas.keySet())) {
            findings.add(new CompatibilityFinding(
                    OPENAPI_SCHEMA_ADDED,
                    ChangeSeverity.MINOR,
                    false,
                    "components.schemas." + schemaName,
                    "New schema added to components"
            ));
        }

        for (String schemaName : intersection(previousSchemas.keySet(), currentSchemas.keySet())) {
            compareSchema(schemaName, previousSchemas.get(schemaName), currentSchemas.get(schemaName), findings);
        }
        return findings;
    }

    private void compareSchema(String schemaName, Schema<?> previous, Schema<?> current, List<CompatibilityFinding> findings) {
        Map<String, Schema<?>> previousProperties = schemaProperties(previous);
        Map<String, Schema<?>> currentProperties = schemaProperties(current);
        Set<String> previousRequired = requiredFields(previous);
        Set<String> currentRequired = requiredFields(current);

        for (String field : difference(previousProperties.keySet(), currentProperties.keySet())) {
            findings.add(new CompatibilityFinding(
                    OPENAPI_FIELD_REMOVED,
                    ChangeSeverity.MAJOR,
                    true,
                    "components.schemas.%s.%s".formatted(schemaName, field),
                    "Field removed from schema"
            ));
        }
        for (String field : difference(currentProperties.keySet(), previousProperties.keySet())) {
            boolean required = currentRequired.contains(field);
            findings.add(new CompatibilityFinding(
                    required ? OPENAPI_REQUIRED_FIELD_ADDED : OPENAPI_OPTIONAL_FIELD_ADDED,
                    required ? ChangeSeverity.CRITICAL : ChangeSeverity.MINOR,
                    required,
                    "components.schemas.%s.%s".formatted(schemaName, field),
                    required ? "Required field added to schema" : "Optional field added to schema"
            ));
        }

        for (String field : intersection(previousProperties.keySet(), currentProperties.keySet())) {
            String previousType = schemaType(previousProperties.get(field));
            String currentType = schemaType(currentProperties.get(field));
            if (!previousType.equals(currentType)) {
                findings.add(new CompatibilityFinding(
                        OPENAPI_FIELD_TYPE_CHANGED,
                        ChangeSeverity.MAJOR,
                        true,
                        "components.schemas.%s.%s".formatted(schemaName, field),
                        "Field type changed from " + previousType + " to " + currentType
                ));
            }
            if (!previousRequired.contains(field) && currentRequired.contains(field)) {
                findings.add(new CompatibilityFinding(
                        OPENAPI_REQUIRED_FIELD_ADDED,
                        ChangeSeverity.CRITICAL,
                        true,
                        "components.schemas.%s.%s".formatted(schemaName, field),
                        "Existing optional field became required"
                ));
            }
        }
    }

    private Set<String> collectOpenApiEndpoints(OpenAPI openAPI) {
        Set<String> endpoints = new LinkedHashSet<>();
        if (openAPI == null || openAPI.getPaths() == null) {
            return endpoints;
        }
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            Map<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operations = pathEntry.getValue().readOperationsMap();
            if (operations == null) {
                continue;
            }
            for (PathItem.HttpMethod method : operations.keySet()) {
                endpoints.add(method.name() + " " + pathEntry.getKey());
            }
        }
        return endpoints;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Schema<?>> collectOpenApiSchemas(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return Map.of();
        }
        return (Map<String, Schema<?>>) (Map<?, ?>) openAPI.getComponents().getSchemas();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Schema<?>> schemaProperties(Schema<?> schema) {
        if (schema == null || schema.getProperties() == null) {
            return Map.of();
        }
        return (Map<String, Schema<?>>) (Map<?, ?>) schema.getProperties();
    }

    private Set<String> requiredFields(Schema<?> schema) {
        if (schema == null || schema.getRequired() == null) {
            return Set.of();
        }
        return Set.copyOf(schema.getRequired());
    }

    private String schemaType(Schema<?> schema) {
        if (schema == null) {
            return "unknown";
        }
        if (schema.getType() != null) {
            if ("array".equals(schema.getType()) && schema.getItems() != null) {
                return "array<" + schemaType(schema.getItems()) + ">";
            }
            String format = schema.getFormat();
            return format == null ? schema.getType() : schema.getType() + ":" + format;
        }
        if (schema.get$ref() != null) {
            return "ref:" + schema.get$ref();
        }
        return "object";
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }
}
