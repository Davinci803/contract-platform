package ru.vkr.contracts.worker.generation.core.build;

import java.nio.file.Path;

public record GenerationPaths(
        Path sourceRoot,
        Path targetRoot
) {
}
