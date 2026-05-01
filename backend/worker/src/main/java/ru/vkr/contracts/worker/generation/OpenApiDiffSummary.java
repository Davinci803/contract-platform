package ru.vkr.contracts.worker.generation;

public record OpenApiDiffSummary(
        int addedEndpoints,
        int removedEndpoints,
        int addedSchemas,
        int removedSchemas,
        boolean breaking
) {
    public static OpenApiDiffSummary initial() {
        return new OpenApiDiffSummary(0, 0, 0, 0, false);
    }
}
