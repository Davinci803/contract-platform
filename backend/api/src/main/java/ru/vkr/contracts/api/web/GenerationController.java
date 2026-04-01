package ru.vkr.contracts.api.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.vkr.contracts.api.dto.JobResponse;
import ru.vkr.contracts.api.service.GenerationJobService;
import ru.vkr.contracts.shared.dto.GenerateRequest;

@RestController
@RequestMapping("/api/generation-jobs")
public class GenerationController {
    private final GenerationJobService generationJobService;

    public GenerationController(GenerationJobService generationJobService) {
        this.generationJobService = generationJobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobResponse create(@Valid @RequestBody GenerateRequest request) {
        return generationJobService.create(request.contractVersionId());
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable Long jobId) {
        return generationJobService.get(jobId);
    }
}
