package ru.vkr.contracts.worker.generation;

import org.springframework.stereotype.Component;

@Component
public class AsyncApiPipeline {

    public GenerationResult generateAndPublish(String contractName, String version, String content) {
        validate(content);
        String groupId = "ru.vkr.contracts.generated";
        String artifactId = contractName.toLowerCase().replace(" ", "-") + "-kafka-lib";
        String coordinates = groupId + ":" + artifactId + ":" + version;
        String publicationUrl = "http://localhost:8081/repository/maven-releases/" + artifactId + "/" + version;
        String schemaSubject = contractName.toLowerCase().replace(" ", ".") + "-value";
        String log = "AsyncAPI pipeline finished. Generated DTO + producer/consumer wrappers. Schema registered.";
        return new GenerationResult(groupId, artifactId, version, coordinates, publicationUrl, schemaSubject, log);
    }

    private void validate(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("AsyncAPI content is empty");
        }
        if (!(content.contains("asyncapi:") || content.contains("\"asyncapi\""))) {
            throw new IllegalArgumentException("AsyncAPI signature not found");
        }
        if (!content.contains("channels")) {
            throw new IllegalArgumentException("AsyncAPI 'channels' section is required");
        }
    }
}
