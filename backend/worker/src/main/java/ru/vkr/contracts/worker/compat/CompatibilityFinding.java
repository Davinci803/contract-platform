package ru.vkr.contracts.worker.compat;

import java.util.Map;

public record CompatibilityFinding(
        String code,
        ChangeSeverity severity,
        boolean breaking,
        String location,
        String message
) {
    public String toHumanReadable() {
        return "[" + severity + "] " + code + " at " + location + ": " + message;
    }

    public Map<String, Object> toMachineReadable() {
        return Map.of(
                "code", code,
                "severity", severity.name(),
                "breaking", breaking,
                "location", location,
                "message", message
        );
    }
}
