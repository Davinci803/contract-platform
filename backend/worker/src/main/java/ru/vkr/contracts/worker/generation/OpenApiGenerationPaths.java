package ru.vkr.contracts.worker.generation;

import java.nio.file.Path;

public record OpenApiGenerationPaths(
        Path sourceRoot,
        Path targetRoot
) {
}
