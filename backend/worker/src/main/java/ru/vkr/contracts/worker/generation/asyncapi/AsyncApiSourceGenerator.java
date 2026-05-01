package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.stereotype.Component;
import ru.vkr.contracts.worker.generation.core.build.GenerationPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class AsyncApiSourceGenerator {

    public GenerationPaths generate(
            Path workspace,
            String basePackage,
            String contractName,
            String artifactId,
            String version,
            AsyncApiSpec spec,
            StringBuilder log
    ) throws IOException {
        appendStage(log, "generation", "generating AsyncAPI Java sources");
        Path sourceRoot = workspace.resolve("src/main/java");
        Path dtoDir = sourceRoot.resolve(basePackage.replace('.', '/')).resolve("dto");
        Path clientDir = sourceRoot.resolve(basePackage.replace('.', '/')).resolve("messaging");
        Files.createDirectories(dtoDir);
        Files.createDirectories(clientDir);

        int dtoCount = generateDtoClasses(spec, dtoDir);
        generateProducerClass(spec, clientDir, basePackage, contractName);
        generateConsumerClass(spec, clientDir, basePackage, contractName);
        generateMetadataClass(spec, clientDir, basePackage, artifactId, version);
        appendStage(log, "generation", "generated dto=" + dtoCount + ", producer=1, consumer=1, metadata=1");
        return new GenerationPaths(sourceRoot, workspace.resolve("target"));
    }

    private int generateDtoClasses(AsyncApiSpec spec, Path dtoDir) throws IOException {
        int written = 0;
        List<AsyncApiSpec.MessageDefinition> messages = spec.messages();
        for (int i = 0; i < messages.size(); i++) {
            AsyncApiSpec.MessageDefinition message = messages.get(i);
            String className = toClassName(message.messageName()) + "Payload";
            Map<String, Object> properties = payloadProperties(message.payloadSchema());
            String packageName = extractPackage(dtoDir);
            String source = renderDto(packageName, className, properties);
            Files.writeString(dtoDir.resolve(className + ".java"), source, StandardCharsets.UTF_8);
            written++;
            if (i > 32) {
                // Protect pipeline from runaway generation on malformed specs.
                break;
            }
        }
        if (written == 0) {
            String packageName = extractPackage(dtoDir);
            Files.writeString(dtoDir.resolve("GeneratedEventPayload.java"), """
                    package %s;

                    public record GeneratedEventPayload(String value) {
                    }
                    """.formatted(packageName), StandardCharsets.UTF_8);
            return 1;
        }
        return written;
    }

    private void generateProducerClass(AsyncApiSpec spec, Path clientDir, String basePackage, String contractName) throws IOException {
        String packageName = basePackage + ".messaging";
        StringJoiner methods = new StringJoiner("\n\n");
        for (AsyncApiSpec.MessageDefinition message : spec.messages()) {
            String methodName = "publish" + toClassName(message.messageName());
            methods.add("""
                        public String %s() {
                            return "PUBLISH %s -> %s";
                        }
                    """.formatted(methodName, escape(message.channelName()), escape(message.messageName())).indent(4));
        }
        String source = """
                package %s;

                public class GeneratedProducer {

                    public String contractName() {
                        return "%s";
                    }

                %s
                }
                """.formatted(packageName, escape(contractName), methods.toString());
        Files.writeString(clientDir.resolve("GeneratedProducer.java"), source, StandardCharsets.UTF_8);
    }

    private void generateConsumerClass(AsyncApiSpec spec, Path clientDir, String basePackage, String contractName) throws IOException {
        String packageName = basePackage + ".messaging";
        StringJoiner methods = new StringJoiner("\n\n");
        for (AsyncApiSpec.MessageDefinition message : spec.messages()) {
            String methodName = "handle" + toClassName(message.messageName());
            methods.add("""
                        public String %s() {
                            return "SUBSCRIBE %s -> %s";
                        }
                    """.formatted(methodName, escape(message.channelName()), escape(message.messageName())).indent(4));
        }
        String source = """
                package %s;

                public class GeneratedConsumer {

                    public String contractName() {
                        return "%s";
                    }

                %s
                }
                """.formatted(packageName, escape(contractName), methods.toString());
        Files.writeString(clientDir.resolve("GeneratedConsumer.java"), source, StandardCharsets.UTF_8);
    }

    private void generateMetadataClass(
            AsyncApiSpec spec,
            Path clientDir,
            String basePackage,
            String artifactId,
            String version
    ) throws IOException {
        String source = """
                package %s.messaging;

                public final class PipelineMetadata {
                    private PipelineMetadata() {
                    }

                    public static final String ARTIFACT_ID = "%s";
                    public static final String VERSION = "%s";
                    public static final int CHANNEL_MESSAGES = %d;
                    public static final String PRIMARY_MESSAGE = "%s";
                }
                """.formatted(
                basePackage,
                artifactId,
                version,
                spec.messages().size(),
                escape(spec.primaryMessageName())
        );
        Files.writeString(clientDir.resolve("PipelineMetadata.java"), source, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadProperties(Map<String, Object> payloadSchema) {
        Object propertiesNode = payloadSchema.get("properties");
        if (!(propertiesNode instanceof Map<?, ?> rawProperties)) {
            return Map.of("value", Map.of("type", "string"));
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawProperties.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) {
                normalized.put(name, (Map<String, Object>) value);
            }
        }
        if (normalized.isEmpty()) {
            normalized.put("value", Map.of("type", "string"));
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private String renderDto(String packageName, String className, Map<String, Object> properties) {
        StringBuilder source = new StringBuilder();
        source.append("package ").append(packageName).append(";\n\n");
        boolean needsList = properties.values().stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .map(property -> String.valueOf(property.get("type")))
                .anyMatch("array"::equalsIgnoreCase);
        if (needsList) {
            source.append("import java.util.List;\n\n");
        }
        source.append("public record ").append(className).append("(");
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> property = (Map<String, Object>) entry.getValue();
            fields.add(javaType(property) + " " + toFieldName(entry.getKey()));
        }
        source.append(String.join(", ", fields));
        source.append(") {\n}\n");
        return source.toString();
    }

    private String javaType(Map<String, Object> propertySchema) {
        String type = String.valueOf(propertySchema.get("type"));
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
        if (Objects.equals(type, "array")) {
            return "List<Object>";
        }
        return "Object";
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
            builder.append("GeneratedEvent");
        }
        if (!Character.isJavaIdentifierStart(builder.charAt(0))) {
            builder.insert(0, 'E');
        }
        return builder.toString();
    }

    private String toFieldName(String value) {
        String className = toClassName(value);
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
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
