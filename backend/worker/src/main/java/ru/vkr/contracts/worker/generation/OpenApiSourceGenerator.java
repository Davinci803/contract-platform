package ru.vkr.contracts.worker.generation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Component
public class OpenApiSourceGenerator {

    public OpenApiGenerationPaths generate(
            Path workspace,
            String basePackage,
            String contractName,
            String artifactId,
            String version,
            OpenAPI openAPI,
            OpenApiDiffSummary diffSummary,
            StringBuilder log
    ) throws IOException {
        appendStage(log, "generation", "generating Java sources");
        Path sourceRoot = workspace.resolve("src/main/java");
        Path dtoDir = sourceRoot.resolve(basePackage.replace('.', '/')).resolve("dto");
        Path clientDir = sourceRoot.resolve(basePackage.replace('.', '/')).resolve("client");
        Files.createDirectories(dtoDir);
        Files.createDirectories(clientDir);

        int dtoCount = generateDtoClasses(openAPI, dtoDir);
        generateClientClass(openAPI, clientDir, basePackage, contractName);
        generateMetadataClass(clientDir, basePackage, artifactId, version, diffSummary);
        appendStage(log, "generation", "generated dto=" + dtoCount + ", client=1, metadata=1");
        return new OpenApiGenerationPaths(sourceRoot, workspace.resolve("target"));
    }

    @SuppressWarnings("unchecked")
    private int generateDtoClasses(OpenAPI openAPI, Path dtoDir) throws IOException {
        Map<String, Schema> schemas = openAPI.getComponents() == null ? Map.of() : openAPI.getComponents().getSchemas();
        if (schemas == null || schemas.isEmpty()) {
            Path fallback = dtoDir.resolve("GeneratedPayload.java");
            Files.writeString(fallback, """
                    package PLACEHOLDER;

                    public class GeneratedPayload {
                    }
                    """.replace("PLACEHOLDER", extractPackage(dtoDir)), StandardCharsets.UTF_8);
            return 1;
        }
        int written = 0;
        for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
            String className = toClassName(entry.getKey());
            Schema<?> schema = entry.getValue();
            Map<String, Schema> properties = schema.getProperties() == null
                    ? Map.of()
                    : (Map<String, Schema>) schema.getProperties();
            String packageName = extractPackage(dtoDir);
            String source = renderDto(packageName, className, properties);
            Files.writeString(dtoDir.resolve(className + ".java"), source, StandardCharsets.UTF_8);
            written++;
        }
        return written;
    }

    private String renderDto(String packageName, String className, Map<String, Schema> properties) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        boolean needsList = properties.values().stream().anyMatch(schema -> schema instanceof ArraySchema);
        boolean needsMap = properties.values().stream().anyMatch(schema -> "object".equals(schema.getType()));
        if (needsList) {
            source.append("import java.util.List;\n");
        }
        if (needsMap) {
            source.append("import java.util.Map;\n");
        }
        if (needsList || needsMap) {
            source.append("\n");
        }
        source.append("public record ").append(className).append("(");
        if (properties.isEmpty()) {
            source.append("String value");
        } else {
            String fields = properties.entrySet().stream()
                    .map(e -> javaType(e.getValue()) + " " + toFieldName(e.getKey()))
                    .collect(Collectors.joining(", "));
            source.append(fields);
        }
        source.append(") {\n}\n");
        return source.toString();
    }

    private void generateClientClass(OpenAPI openAPI, Path clientDir, String basePackage, String contractName) throws IOException {
        String packageName = basePackage + ".client";
        String className = "GeneratedApiClient";
        StringJoiner methods = new StringJoiner("\n\n");
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                Map<PathItem.HttpMethod, Operation> operations = pathEntry.getValue().readOperationsMap();
                if (operations == null) {
                    continue;
                }
                for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : operations.entrySet()) {
                    String methodName = operationEntry.getValue().getOperationId();
                    if (methodName == null || methodName.isBlank()) {
                        methodName = operationEntry.getKey().name().toLowerCase(Locale.ROOT)
                                + toClassName(pathEntry.getKey()).replace("Api", "");
                    }
                    methods.add("    public String " + sanitizeIdentifier(methodName) + "() {\n"
                            + "        return \"" + operationEntry.getKey().name() + " " + pathEntry.getKey() + "\";\n"
                            + "    }");
                }
            }
        }
        if (methods.length() == 0) {
            methods.add("    public String health() {\n        return \"No operations\";\n    }");
        }
        String source = """
                package %s;

                public class %s {

                    public String contractName() {
                        return "%s";
                    }

                %s
                }
                """.formatted(packageName, className, escape(contractName), methods);
        Files.writeString(clientDir.resolve(className + ".java"), source, StandardCharsets.UTF_8);
    }

    private void generateMetadataClass(
            Path clientDir,
            String basePackage,
            String artifactId,
            String version,
            OpenApiDiffSummary diffSummary
    ) throws IOException {
        String source = """
                package %s.client;

                public final class PipelineMetadata {
                    private PipelineMetadata() {
                    }

                    public static final String ARTIFACT_ID = "%s";
                    public static final String VERSION = "%s";
                    public static final int ADDED_ENDPOINTS = %d;
                    public static final int REMOVED_ENDPOINTS = %d;
                    public static final int ADDED_SCHEMAS = %d;
                    public static final int REMOVED_SCHEMAS = %d;
                    public static final boolean BREAKING_CHANGE = %s;
                }
                """.formatted(
                basePackage,
                artifactId,
                version,
                diffSummary.addedEndpoints(),
                diffSummary.removedEndpoints(),
                diffSummary.addedSchemas(),
                diffSummary.removedSchemas(),
                diffSummary.breaking()
        );
        Files.writeString(clientDir.resolve("PipelineMetadata.java"), source, StandardCharsets.UTF_8);
    }

    private String toClassName(String value) {
        String[] parts = value.replaceAll("[^a-zA-Z0-9]+", " ").trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        if (builder.isEmpty()) {
            builder.append("GeneratedType");
        }
        if (!Character.isJavaIdentifierStart(builder.charAt(0))) {
            builder.insert(0, 'T');
        }
        return builder.toString();
    }

    private String toFieldName(String value) {
        String className = toClassName(value);
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    private String javaType(Schema schema) {
        if (schema == null) {
            return "Object";
        }
        if (schema.get$ref() != null && !schema.get$ref().isBlank()) {
            String ref = schema.get$ref();
            return toClassName(ref.substring(ref.lastIndexOf('/') + 1));
        }
        if (schema instanceof ArraySchema arraySchema) {
            return "List<" + javaType(arraySchema.getItems()) + ">";
        }
        String type = schema.getType();
        if (Objects.equals(type, "string")) {
            return "String";
        }
        if (Objects.equals(type, "integer")) {
            return "Integer";
        }
        if (Objects.equals(type, "number")) {
            return "Double";
        }
        if (Objects.equals(type, "boolean")) {
            return "Boolean";
        }
        if (Objects.equals(type, "object")) {
            return "Map<String, Object>";
        }
        return "Object";
    }

    private String sanitizeIdentifier(String name) {
        String clean = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (clean.isBlank()) {
            clean = "operation";
        }
        if (!Character.isJavaIdentifierStart(clean.charAt(0))) {
            clean = "op_" + clean;
        }
        return clean;
    }

    private String extractPackage(Path dtoDir) {
        String path = dtoDir.toString().replace('\\', '/');
        int index = path.indexOf("/java/");
        if (index < 0) {
            throw new IllegalStateException("Cannot derive package from path: " + dtoDir);
        }
        return path.substring(index + "/java/".length()).replace('/', '.');
    }

    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }
}
