package ru.vkr.contracts.worker.compat;

import org.springframework.stereotype.Component;
import ru.vkr.contracts.shared.model.ContractType;
import ru.vkr.contracts.worker.generation.asyncapi.AsyncApiSpec;
import ru.vkr.contracts.worker.generation.asyncapi.AsyncApiSpecValidator;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
class AsyncApiCompatibilityRulesEngine implements CompatibilityRulesEngine {
    private static final String ASYNCAPI_CHANNEL_REMOVED = "ASYNCAPI_CHANNEL_REMOVED";
    private static final String ASYNCAPI_CHANNEL_ADDED = "ASYNCAPI_CHANNEL_ADDED";
    private static final String ASYNCAPI_MESSAGE_REMOVED = "ASYNCAPI_MESSAGE_REMOVED";
    private static final String ASYNCAPI_MESSAGE_ADDED = "ASYNCAPI_MESSAGE_ADDED";
    private static final String ASYNCAPI_REQUIRED_FIELD_ADDED = "ASYNCAPI_REQUIRED_FIELD_ADDED";
    private static final String ASYNCAPI_OPTIONAL_FIELD_ADDED = "ASYNCAPI_OPTIONAL_FIELD_ADDED";
    private static final String ASYNCAPI_FIELD_REMOVED = "ASYNCAPI_FIELD_REMOVED";
    private static final String ASYNCAPI_FIELD_TYPE_CHANGED = "ASYNCAPI_FIELD_TYPE_CHANGED";

    private final AsyncApiSpecValidator asyncApiSpecValidator;

    AsyncApiCompatibilityRulesEngine(AsyncApiSpecValidator asyncApiSpecValidator) {
        this.asyncApiSpecValidator = asyncApiSpecValidator;
    }

    @Override
    public ContractType supportedType() {
        return ContractType.ASYNCAPI;
    }

    @Override
    public List<CompatibilityFinding> analyze(String previousSpec, String currentSpec) {
        AsyncApiSpec previous = asyncApiSpecValidator.parseAndValidate(previousSpec, new StringBuilder());
        AsyncApiSpec current = asyncApiSpecValidator.parseAndValidate(currentSpec, new StringBuilder());
        List<CompatibilityFinding> findings = new java.util.ArrayList<>();

        Set<String> previousChannels = previous.messages().stream()
                .map(AsyncApiSpec.MessageDefinition::channelName)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        Set<String> currentChannels = current.messages().stream()
                .map(AsyncApiSpec.MessageDefinition::channelName)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        for (String channel : difference(previousChannels, currentChannels)) {
            findings.add(new CompatibilityFinding(
                    ASYNCAPI_CHANNEL_REMOVED,
                    ChangeSeverity.CRITICAL,
                    true,
                    "channels." + channel,
                    "Channel removed from contract"
            ));
        }
        for (String channel : difference(currentChannels, previousChannels)) {
            findings.add(new CompatibilityFinding(
                    ASYNCAPI_CHANNEL_ADDED,
                    ChangeSeverity.MINOR,
                    false,
                    "channels." + channel,
                    "New channel added"
            ));
        }

        Map<String, AsyncApiSpec.MessageDefinition> previousMessages = byMessageKey(previous.messages());
        Map<String, AsyncApiSpec.MessageDefinition> currentMessages = byMessageKey(current.messages());
        for (String messageKey : difference(previousMessages.keySet(), currentMessages.keySet())) {
            findings.add(new CompatibilityFinding(
                    ASYNCAPI_MESSAGE_REMOVED,
                    ChangeSeverity.MAJOR,
                    true,
                    messageKey,
                    "Message removed from channel operation"
            ));
        }
        for (String messageKey : difference(currentMessages.keySet(), previousMessages.keySet())) {
            findings.add(new CompatibilityFinding(
                    ASYNCAPI_MESSAGE_ADDED,
                    ChangeSeverity.MINOR,
                    false,
                    messageKey,
                    "New message added to channel operation"
            ));
        }

        for (String messageKey : intersection(previousMessages.keySet(), currentMessages.keySet())) {
            comparePayload(messageKey, previousMessages.get(messageKey), currentMessages.get(messageKey), findings);
        }
        return findings;
    }

    private void comparePayload(
            String messageKey,
            AsyncApiSpec.MessageDefinition previousMessage,
            AsyncApiSpec.MessageDefinition currentMessage,
            List<CompatibilityFinding> findings
    ) {
        Map<String, Map<String, Object>> previousProperties = payloadProperties(previousMessage.payloadSchema());
        Map<String, Map<String, Object>> currentProperties = payloadProperties(currentMessage.payloadSchema());
        Set<String> previousRequired = payloadRequired(previousMessage.payloadSchema());
        Set<String> currentRequired = payloadRequired(currentMessage.payloadSchema());

        for (String field : difference(previousProperties.keySet(), currentProperties.keySet())) {
            findings.add(new CompatibilityFinding(
                    ASYNCAPI_FIELD_REMOVED,
                    ChangeSeverity.MAJOR,
                    true,
                    messageKey + ".payload." + field,
                    "Payload field removed"
            ));
        }
        for (String field : difference(currentProperties.keySet(), previousProperties.keySet())) {
            boolean required = currentRequired.contains(field);
            findings.add(new CompatibilityFinding(
                    required ? ASYNCAPI_REQUIRED_FIELD_ADDED : ASYNCAPI_OPTIONAL_FIELD_ADDED,
                    required ? ChangeSeverity.CRITICAL : ChangeSeverity.MINOR,
                    required,
                    messageKey + ".payload." + field,
                    required ? "Required payload field added" : "Optional payload field added"
            ));
        }

        for (String field : intersection(previousProperties.keySet(), currentProperties.keySet())) {
            String previousType = payloadType(previousProperties.get(field));
            String currentType = payloadType(currentProperties.get(field));
            if (!previousType.equals(currentType)) {
                findings.add(new CompatibilityFinding(
                        ASYNCAPI_FIELD_TYPE_CHANGED,
                        ChangeSeverity.MAJOR,
                        true,
                        messageKey + ".payload." + field,
                        "Payload field type changed from " + previousType + " to " + currentType
                ));
            }
            if (!previousRequired.contains(field) && currentRequired.contains(field)) {
                findings.add(new CompatibilityFinding(
                        ASYNCAPI_REQUIRED_FIELD_ADDED,
                        ChangeSeverity.CRITICAL,
                        true,
                        messageKey + ".payload." + field,
                        "Existing payload field became required"
                ));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> payloadProperties(Map<String, Object> payloadSchema) {
        Object properties = payloadSchema.get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
            if (entry.getKey() instanceof String name && entry.getValue() instanceof Map<?, ?> details) {
                result.put(name, (Map<String, Object>) details);
            }
        }
        return result;
    }

    private Set<String> payloadRequired(Map<String, Object> payloadSchema) {
        Object requiredRaw = payloadSchema.get("required");
        if (!(requiredRaw instanceof Collection<?> requiredCollection)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object value : requiredCollection) {
            if (value instanceof String fieldName) {
                result.add(fieldName);
            }
        }
        return result;
    }

    private String payloadType(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (!(type instanceof String typeName) || typeName.isBlank()) {
            Object ref = schema.get("$ref");
            if (ref instanceof String refValue && !refValue.isBlank()) {
                return "ref:" + refValue;
            }
            return "object";
        }
        Object format = schema.get("format");
        if (format instanceof String formatName && !formatName.isBlank()) {
            return typeName + ":" + formatName;
        }
        return typeName;
    }

    private Map<String, AsyncApiSpec.MessageDefinition> byMessageKey(List<AsyncApiSpec.MessageDefinition> messages) {
        Map<String, AsyncApiSpec.MessageDefinition> result = new TreeMap<>();
        for (AsyncApiSpec.MessageDefinition message : messages) {
            String key = message.channelName() + "|" + message.operation() + "|" + message.messageName();
            result.put(key, message);
        }
        return result;
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
