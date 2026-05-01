package ru.vkr.contracts.worker.generation.asyncapi;

import org.springframework.stereotype.Component;
import ru.vkr.contracts.worker.generation.core.build.ArtifactBuilder;
import ru.vkr.contracts.worker.generation.core.build.GenerationPaths;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class AsyncApiBuildService {
    private final AsyncApiSpecValidator specValidator;
    private final AsyncApiSourceGenerator sourceGenerator;
    private final ArtifactBuilder artifactBuilder;

    public AsyncApiBuildService(
            AsyncApiSpecValidator specValidator,
            AsyncApiSourceGenerator sourceGenerator,
            ArtifactBuilder artifactBuilder
    ) {
        this.specValidator = specValidator;
        this.sourceGenerator = sourceGenerator;
        this.artifactBuilder = artifactBuilder;
    }

    public AsyncApiBuildOutput build(
            Path workspace,
            String groupId,
            String contractName,
            String artifactId,
            String version,
            String content,
            StringBuilder log
    ) throws IOException {
        AsyncApiSpec spec = specValidator.parseAndValidate(content, log);
        String basePackage = groupId + "." + artifactId.replace("-", ".");
        GenerationPaths paths = sourceGenerator.generate(
                workspace,
                basePackage,
                contractName,
                artifactId,
                version,
                spec,
                log
        );
        Path jarFile = artifactBuilder.buildJar(paths, artifactId, version, log);
        Path pomFile = artifactBuilder.buildPom(paths.targetRoot(), groupId, artifactId, version, log);
        return new AsyncApiBuildOutput(spec, jarFile, pomFile);
    }

    public record AsyncApiBuildOutput(
            AsyncApiSpec spec,
            Path jarFile,
            Path pomFile
    ) {
    }
}
