package ru.vkr.contracts.worker.compat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.vkr.contracts.shared.model.CompatibilityLevel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record CompatibilityResult(
        CompatibilityLevel level,
        String recommendedSemverIncrement,
        List<CompatibilityFinding> findings
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public String findingsAsText() {
        return findings.stream()
                .map(CompatibilityFinding::toHumanReadable)
                .collect(Collectors.joining("; "));
    }

    public String findingsAsJson() {
        List<Map<String, Object>> payload = findings.stream()
                .map(CompatibilityFinding::toMachineReadable)
                .toList();
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return findingsAsText();
        }
    }
}
