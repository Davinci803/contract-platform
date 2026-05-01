package ru.vkr.contracts.worker.generation.asyncapi;

import java.util.List;
import java.util.Map;

public record AsyncApiSpec(
        String title,
        List<AsyncApiSpec.MessageDefinition> messages,
        Map<String, Object> primaryPayloadSchema
) {
    public String primaryMessageName() {
        if (messages == null || messages.isEmpty()) {
            return "event";
        }
        String name = messages.getFirst().messageName();
        return (name == null || name.isBlank()) ? "event" : name;
    }

    public record MessageDefinition(
            String channelName,
            String operation,
            String messageName,
            Map<String, Object> payloadSchema
    ) {
    }
}
