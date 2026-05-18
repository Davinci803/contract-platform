package ru.vkr.contracts.api.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.contracts.api.dto.ArtifactResponse;
import ru.vkr.contracts.api.dto.PublicationLogResponse;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/read-model")
public class ReadModelController {
    private final GeneratedArtifactRepository generatedArtifactRepository;
    private final PublicationLogRepository publicationLogRepository;

    public ReadModelController(
            GeneratedArtifactRepository generatedArtifactRepository,
            PublicationLogRepository publicationLogRepository
    ) {
        this.generatedArtifactRepository = generatedArtifactRepository;
        this.publicationLogRepository = publicationLogRepository;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of(
                "artifacts", generatedArtifactRepository.count(),
                "publicationLogs", publicationLogRepository.count()
        );
    }

    @GetMapping("/artifacts")
    public List<ArtifactResponse> artifacts(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "correlationId", required = false) String correlationId
    ) {
        int cappedLimit = Math.max(1, Math.min(limit, 20));
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        List<ru.vkr.contracts.api.domain.GeneratedArtifact> source = normalizedCorrelationId == null
                ? generatedArtifactRepository.findTop20ByOrderByIdDesc()
                : generatedArtifactRepository.findTop20ByJob_CorrelationIdOrderByIdDesc(normalizedCorrelationId);
        return source.stream()
                .limit(cappedLimit)
                .map(artifact -> new ArtifactResponse(
                        artifact.getId(),
                        artifact.getJobId(),
                        artifact.getCoordinates(),
                        artifact.getPublicationUrl(),
                        artifact.getSchemaSubject()
                ))
                .toList();
    }

    @GetMapping("/publication-logs")
    public List<PublicationLogResponse> publicationLogs(
            @RequestParam(name = "limit", defaultValue = "30") int limit,
            @RequestParam(name = "correlationId", required = false) String correlationId
    ) {
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        String normalizedCorrelationId = normalizeCorrelationId(correlationId);
        List<ru.vkr.contracts.api.domain.PublicationLog> source = normalizedCorrelationId == null
                ? publicationLogRepository.findTop50ByOrderByIdDesc()
                : publicationLogRepository.findTop50ByCorrelationIdOrderByIdDesc(normalizedCorrelationId);
        return source.stream()
                .limit(cappedLimit)
                .map(log -> new PublicationLogResponse(
                        log.getId(),
                        log.getJobId(),
                        log.getTarget(),
                        log.getStatus(),
                        log.getMessage(),
                        log.getEventType(),
                        log.getErrorCategory(),
                        log.getCorrelationId()
                ))
                .toList();
    }

    private String normalizeCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return null;
        }
        return correlationId.trim();
    }
}
