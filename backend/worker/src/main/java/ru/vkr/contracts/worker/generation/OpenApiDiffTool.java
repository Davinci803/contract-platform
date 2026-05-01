package ru.vkr.contracts.worker.generation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class OpenApiDiffTool {

    public OpenApiDiffSummary diff(OpenAPI previous, OpenAPI current) {
        Set<String> previousEndpoints = collectEndpoints(previous);
        Set<String> currentEndpoints = collectEndpoints(current);
        Set<String> previousSchemas = collectSchemas(previous);
        Set<String> currentSchemas = collectSchemas(current);

        int addedEndpoints = difference(currentEndpoints, previousEndpoints).size();
        int removedEndpoints = difference(previousEndpoints, currentEndpoints).size();
        int addedSchemas = difference(currentSchemas, previousSchemas).size();
        int removedSchemas = difference(previousSchemas, currentSchemas).size();
        boolean breaking = removedEndpoints > 0 || removedSchemas > 0;

        return new OpenApiDiffSummary(
                addedEndpoints,
                removedEndpoints,
                addedSchemas,
                removedSchemas,
                breaking
        );
    }

    private Set<String> collectEndpoints(OpenAPI openAPI) {
        Set<String> endpoints = new TreeSet<>();
        if (openAPI == null || openAPI.getPaths() == null) {
            return endpoints;
        }
        for (Map.Entry<String, PathItem> path : openAPI.getPaths().entrySet()) {
            Map<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operations = path.getValue().readOperationsMap();
            if (operations == null) {
                continue;
            }
            for (PathItem.HttpMethod method : operations.keySet()) {
                endpoints.add(method.name() + " " + path.getKey());
            }
        }
        return endpoints;
    }

    private Set<String> collectSchemas(OpenAPI openAPI) {
        Set<String> schemas = new TreeSet<>();
        if (openAPI == null || openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return schemas;
        }
        for (Map.Entry<String, Schema> entry : openAPI.getComponents().getSchemas().entrySet()) {
            schemas.add(entry.getKey());
        }
        return schemas;
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }
}
