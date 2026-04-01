package ru.vkr.contracts.api.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;

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
}
