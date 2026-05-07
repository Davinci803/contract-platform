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
    public List<ArtifactResponse> artifacts(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 20));
        return generatedArtifactRepository.findTop20ByOrderByIdDesc().stream()
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
    public List<PublicationLogResponse> publicationLogs(@RequestParam(name = "limit", defaultValue = "30") int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 50));
        return publicationLogRepository.findTop50ByOrderByIdDesc().stream()
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
}
