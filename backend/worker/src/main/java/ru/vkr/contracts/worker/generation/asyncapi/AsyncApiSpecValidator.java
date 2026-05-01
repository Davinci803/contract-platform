package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AsyncApiSpecValidator {

    public AsyncApiSpec parseAndValidate(String content, StringBuilder log) {
        appendStage(log, "validation", "parsing AsyncAPI spec");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("AsyncAPI content is empty");
        }
        Object parsed = new Yaml().load(content);
        if (!(parsed instanceof Map<?, ?> rootRaw)) {
            throw new IllegalArgumentException("AsyncAPI parse failed: root object is missing");
        }
        Map<String, Object> root = castMap(rootRaw, "root");
        Object version = root.get("asyncapi");
        if (!(version instanceof String versionText) || versionText.isBlank()) {
            throw new IllegalArgumentException("AsyncAPI version is missing");
        }
        Map<String, Object> channels = requiredMap(root, "channels");
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("AsyncAPI channels section is empty");
        }
        Map<String, Object> components = optionalMap(root.get("components"), "components");
        Map<String, Object> componentMessages = optionalMap(components.get("messages"), "components.messages");
        Map<String, Object> componentSchemas = optionalMap(components.get("schemas"), "components.schemas");

        List<AsyncApiSpec.MessageDefinition> messageDefinitions = new ArrayList<>();
        for (Map.Entry<String, Object> channelEntry : channels.entrySet()) {
            String channelName = channelEntry.getKey();
            Map<String, Object> channel = castMap(channelEntry.getValue(), "channel '" + channelName + "'");
            collectOperationMessage(
                    messageDefinitions,
                    channelName,
                    "publish",
                    channel.get("publish"),
                    componentMessages,
                    componentSchemas
            );
            collectOperationMessage(
                    messageDefinitions,
                    channelName,
                    "subscribe",
                    channel.get("subscribe"),
                    componentMessages,
                    componentSchemas
            );
        }
        if (messageDefinitions.isEmpty()) {
            throw new IllegalArgumentException("AsyncAPI contains no publish/subscribe message payloads");
        }

        Map<String, Object> primaryPayload = messageDefinitions.getFirst().payloadSchema();
        if (primaryPayload.isEmpty()) {
            throw new IllegalArgumentException("AsyncAPI message payload is empty");
        }

        String title = extractTitle(root);
        appendStage(
                log,
                "validation",
                "parsed successfully, asyncapi=" + versionText + ", channels=" + channels.size() + ", messages=" + messageDefinitions.size()
        );
        return new AsyncApiSpec(title, List.copyOf(messageDefinitions), Map.copyOf(primaryPayload));
    }

    private void collectOperationMessage(
            List<AsyncApiSpec.MessageDefinition> output,
            String channelName,
            String operation,
            Object operationNode,
            Map<String, Object> componentMessages,
            Map<String, Object> componentSchemas
    ) {
        if (operationNode == null) {
            return;
        }
        Map<String, Object> operationMap = castMap(operationNode, "operation '" + operation + "' in channel '" + channelName + "'");
        Object messageNode = operationMap.get("message");
        if (messageNode == null) {
            return;
        }
        List<Map<String, Object>> resolvedMessages = resolveMessageVariants(messageNode, componentMessages);
        for (Map<String, Object> message : resolvedMessages) {
            String messageName = resolveMessageName(channelName, operation, message);
            Map<String, Object> payload = resolvePayload(message.get("payload"), componentSchemas);
            output.add(new AsyncApiSpec.MessageDefinition(channelName, operation, messageName, payload));
        }
    }

    private List<Map<String, Object>> resolveMessageVariants(Object messageNode, Map<String, Object> componentMessages) {
        Map<String, Object> resolvedMessage = resolveMessage(messageNode, componentMessages);
        Object oneOf = resolvedMessage.get("oneOf");
        if (!(oneOf instanceof List<?> variants) || variants.isEmpty()) {
            return List.of(resolvedMessage);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object variant : variants) {
            result.add(resolveMessage(variant, componentMessages));
        }
        return result;
    }

    private Map<String, Object> resolveMessage(Object messageNode, Map<String, Object> componentMessages) {
        Map<String, Object> message = castMap(messageNode, "message");
        Object refNode = message.get("$ref");
        if (!(refNode instanceof String ref) || ref.isBlank()) {
            return message;
        }
        String messageKey = ref.substring(ref.lastIndexOf('/') + 1);
        Object referenced = componentMessages.get(messageKey);
        if (referenced == null) {
            throw new IllegalArgumentException("AsyncAPI message reference not found: " + ref);
        }
        return castMap(referenced, "message reference '" + ref + "'");
    }

    private Map<String, Object> resolvePayload(Object payloadNode, Map<String, Object> componentSchemas) {
        Map<String, Object> payload = castMap(payloadNode, "message payload");
        Object refNode = payload.get("$ref");
        if (!(refNode instanceof String ref) || ref.isBlank()) {
            return payload;
        }
        String schemaKey = ref.substring(ref.lastIndexOf('/') + 1);
        Object schema = componentSchemas.get(schemaKey);
        if (schema == null) {
            throw new IllegalArgumentException("AsyncAPI payload reference not found: " + ref);
        }
        return castMap(schema, "payload reference '" + ref + "'");
    }

    private String resolveMessageName(String channelName, String operation, Map<String, Object> message) {
        Object title = message.get("title");
        if (title instanceof String titleText && !titleText.isBlank()) {
            return titleText;
        }
        Object name = message.get("name");
        if (name instanceof String nameText && !nameText.isBlank()) {
            return nameText;
        }
        return channelName + "-" + operation;
    }

    private String extractTitle(Map<String, Object> root) {
        Map<String, Object> info = optionalMap(root.get("info"), "info");
        Object title = info.get("title");
        if (title instanceof String titleText && !titleText.isBlank()) {
            return titleText;
        }
        return "Async API";
    }

    private Map<String, Object> requiredMap(Map<String, Object> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof Map<?, ?> mapValue)) {
            throw new IllegalArgumentException("AsyncAPI '" + field + "' section is required");
        }
        return castMap(mapValue, field);
    }

    private Map<String, Object> optionalMap(Object value, String field) {
        if (value == null) {
            return Map.of();
        }
        return castMap(value, field);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("AsyncAPI parse failed: " + label + " must be an object");
        }
        return (Map<String, Object>) map;
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }
}
