package ru.vkr.contracts.worker.generation;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class OpenApiPipeline {
    private final String groupId;
    private final String artifactSuffix;
    private final OpenApiDiffTool openApiDiffTool;
    private final OpenApiSpecValidator specValidator;
    private final OpenApiSourceGenerator sourceGenerator;
    private final OpenApiArtifactBuilder artifactBuilder;
    private final OpenApiNexusPublisher nexusPublisher;

    @Autowired
    public OpenApiPipeline(
            @Value("${generation.open-api.group-id:ru.vkr.contracts.generated}") String groupId,
            @Value("${generation.open-api.artifact-suffix:rest-client}") String artifactSuffix,
            OpenApiDiffTool openApiDiffTool,
            OpenApiSpecValidator specValidator,
            OpenApiSourceGenerator sourceGenerator,
            OpenApiArtifactBuilder artifactBuilder,
            OpenApiNexusPublisher nexusPublisher
    ) {
        this.groupId = groupId;
        this.artifactSuffix = artifactSuffix;
        this.openApiDiffTool = openApiDiffTool;
        this.specValidator = specValidator;
        this.sourceGenerator = sourceGenerator;
        this.artifactBuilder = artifactBuilder;
        this.nexusPublisher = nexusPublisher;
    }

    public OpenApiPipeline(
            String groupId,
            String artifactSuffix,
            String nexusBaseUrl,
            String nexusRepository,
            String nexusUsername,
            String nexusPassword,
            OpenApiDiffTool openApiDiffTool
    ) {
        this(
                groupId,
                artifactSuffix,
                openApiDiffTool,
                new OpenApiSpecValidator(),
                new OpenApiSourceGenerator(),
                new OpenApiArtifactBuilder(),
                new OpenApiNexusPublisher(
                        nexusBaseUrl,
                        nexusRepository,
                        nexusUsername,
                        nexusPassword,
                        HttpClient.newBuilder().build()
                )
        );
    }

    public GenerationResult generateAndPublish(String contractName, String version, String content) {
        return generateAndPublish(contractName, version, content, null);
    }

    public GenerationResult generateAndPublish(String contractName, String version, String content, String previousVersionContent) {
        StringBuilder log = new StringBuilder();
        Path workspace = null;
        try {
            OpenAPI current = specValidator.parseAndValidate(content, "current", log);
            OpenApiDiffSummary diffSummary = calculateDiff(previousVersionContent, current, log);
            workspace = Files.createTempDirectory("openapi-pipeline-");

            String artifactId = toArtifactId(contractName);
            String basePackage = groupId + "." + artifactId.replace("-", ".");
            OpenApiGenerationPaths paths = sourceGenerator.generate(
                    workspace,
                    basePackage,
                    contractName,
                    artifactId,
                    version,
                    current,
                    diffSummary,
                    log
            );
            Path jarFile = artifactBuilder.buildJar(paths, artifactId, version, log);
            Path pomFile = artifactBuilder.buildPom(paths.targetRoot(), groupId, artifactId, version, log);
            String publicationUrl = nexusPublisher.publish(groupId, artifactId, version, jarFile, pomFile, log);
            String coordinates = groupId + ":" + artifactId + ":" + version;
            appendStage(log, "pipeline", "finished successfully");
            return new GenerationResult(groupId, artifactId, version, coordinates, publicationUrl, null, log.toString());
        } catch (IOException e) {
            throw new IllegalStateException("OpenAPI pipeline I/O failure: " + e.getMessage(), e);
        } finally {
            cleanupWorkspace(workspace);
        }
    }

    private OpenApiDiffSummary calculateDiff(String previousContent, OpenAPI current, StringBuilder log) {
        appendStage(log, "diff", "starting");
        if (previousContent == null || previousContent.isBlank()) {
            appendStage(log, "diff", "initial version detected, no previous spec");
            return OpenApiDiffSummary.initial();
        }
        OpenAPI previous = specValidator.parseAndValidate(previousContent, "previous", log);
        OpenApiDiffSummary summary = openApiDiffTool.diff(previous, current);
        appendStage(
                log,
                "diff",
                "addedEndpoints=" + summary.addedEndpoints()
                        + ", removedEndpoints=" + summary.removedEndpoints()
                        + ", addedSchemas=" + summary.addedSchemas()
                        + ", removedSchemas=" + summary.removedSchemas()
                        + ", breaking=" + summary.breaking()
        );
        return summary;
    }

    private String toArtifactId(String contractName) {
        String normalized = contractName == null
                ? "contract"
                : contractName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String compact = normalized.replaceAll("(^-+|-+$)", "");
        if (compact.isBlank()) {
            compact = "contract";
        }
        return compact + "-" + artifactSuffix;
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
