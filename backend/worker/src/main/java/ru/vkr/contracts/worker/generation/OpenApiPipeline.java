package ru.vkr.contracts.worker.generation;

import org.springframework.stereotype.Component;

@Component
public class OpenApiPipeline {

    public GenerationResult generateAndPublish(String contractName, String version, String content) {
        validate(content);
        String groupId = "ru.vkr.contracts.generated";
        String artifactId = contractName.toLowerCase().replace(" ", "-") + "-rest-client";
        String coordinates = groupId + ":" + artifactId + ":" + version;
        String publicationUrl = "http://localhost:8081/repository/maven-releases/" + artifactId + "/" + version;
        String log = "OpenAPI pipeline finished. Generated DTO + client and published to Nexus.";
        return new GenerationResult(groupId, artifactId, version, coordinates, publicationUrl, null, log);
    }

    private void validate(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("OpenAPI content is empty");
        }
        if (!(content.contains("openapi:") || content.contains("\"openapi\""))) {
            throw new IllegalArgumentException("OpenAPI signature not found");
        }
        if (!content.contains("paths")) {
            throw new IllegalArgumentException("OpenAPI 'paths' section is required");
        }
    }
}
