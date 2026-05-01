package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.vkr.contracts.worker.generation.core.build.ArtifactBuilder;
import ru.vkr.contracts.worker.generation.model.GenerationResult;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
public class AsyncApiPipeline {
    private final String groupId;
    private final AsyncApiNamingStrategy namingStrategy;
    private final AsyncApiBuildService buildService;
    private final AsyncApiPublicationService publicationService;

    @Autowired
    public AsyncApiPipeline(
            @Value("${generation.async-api.group-id:ru.vkr.contracts.generated}") String groupId,
            AsyncApiNamingStrategy namingStrategy,
            AsyncApiBuildService buildService,
            AsyncApiPublicationService publicationService
    ) {
        this.groupId = groupId;
        this.namingStrategy = namingStrategy;
        this.buildService = buildService;
        this.publicationService = publicationService;
    }

    public AsyncApiPipeline(
            String groupId,
            String artifactSuffix,
            String subjectSuffix,
            String nexusBaseUrl,
            String nexusRepository,
            String nexusUsername,
            String nexusPassword,
            String schemaRegistryBaseUrl,
            String schemaRegistryCompatibility
    ) {
        this(
                groupId,
                artifactSuffix,
                subjectSuffix,
                nexusBaseUrl,
                nexusRepository,
                nexusUsername,
                nexusPassword,
                schemaRegistryBaseUrl,
                schemaRegistryCompatibility,
                "AVRO"
        );
    }

    public AsyncApiPipeline(
            String groupId,
            String artifactSuffix,
            String subjectSuffix,
            String nexusBaseUrl,
            String nexusRepository,
            String nexusUsername,
            String nexusPassword,
            String schemaRegistryBaseUrl,
            String schemaRegistryCompatibility,
            String schemaType
    ) {
        this(
                groupId,
                new AsyncApiNamingStrategy(artifactSuffix, subjectSuffix),
                new AsyncApiBuildService(
                        new AsyncApiSpecValidator(),
                        new AsyncApiSourceGenerator(),
                        new ArtifactBuilder()
                ),
                new AsyncApiPublicationService(
                        new AsyncApiNexusPublisher(
                                nexusBaseUrl,
                                nexusRepository,
                                nexusUsername,
                                nexusPassword,
                                HttpClient.newBuilder().build()
                        ),
                        new AsyncApiSchemaRegistryClient(
                                schemaRegistryBaseUrl,
                                "",
                                "",
                                schemaRegistryCompatibility,
                                HttpClient.newBuilder().build()
                        ),
                        schemaType
                )
        );
    }

    public GenerationResult generateAndPublish(String contractName, String version, String content) {
        StringBuilder log = new StringBuilder();
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("asyncapi-pipeline-");
            String artifactId = namingStrategy.toArtifactId(contractName);

            AsyncApiBuildService.AsyncApiBuildOutput buildOutput = buildService.build(
                    workspace,
                    groupId,
                    contractName,
                    artifactId,
                    version,
                    content,
                    log
            );

            String schemaSubject = namingStrategy.buildSubject(contractName, buildOutput.spec().primaryMessageName());
            AsyncApiPublicationService.AsyncApiPublicationResult publicationResult = publicationService.registerSchemaAndPublish(
                    groupId,
                    artifactId,
                    version,
                    schemaSubject,
                    buildOutput.spec(),
                    buildOutput.jarFile(),
                    buildOutput.pomFile(),
                    log
            );
            String coordinates = groupId + ":" + artifactId + ":" + version;
            appendStage(log, "pipeline", "finished successfully");
            return new GenerationResult(
                    groupId,
                    artifactId,
                    version,
                    coordinates,
                    publicationResult.publicationUrl(),
                    publicationResult.schemaSubject(),
                    log.toString()
            );
        } catch (IOException e) {
            throw new IllegalStateException("AsyncAPI pipeline I/O failure: " + e.getMessage(), e);
        } finally {
            cleanupWorkspace(workspace);
        }
    }

    private void cleanupWorkspace(Path workspace) {
        if (workspace == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(workspace)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temp cleanup should not break pipeline result
                }
            });
        } catch (IOException ignored) {
            // Temp cleanup should not break pipeline result
        }
    }

    private void appendStage(StringBuilder log, String stage, String message) {
        if (!log.isEmpty()) {
            log.append('\n');
        }
        log.append('[').append(stage).append("] ").append(message);
    }
}
