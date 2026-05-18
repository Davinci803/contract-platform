package ru.vkr.contracts.api.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.contracts.api.domain.CompatibilityReport;
import ru.vkr.contracts.api.service.CompatibilityService;
import ru.vkr.contracts.shared.dto.GenerateRequest;

import java.util.List;

@RestController
@RequestMapping("/api/compatibility-reports")
public class CompatibilityController {
    private final CompatibilityService compatibilityService;

    public CompatibilityController(CompatibilityService compatibilityService) {
        this.compatibilityService = compatibilityService;
    }

    @GetMapping
    public List<CompatibilityReport> list() {
        return compatibilityService.list();
    }

    @GetMapping(params = "contractVersionId")
    public CompatibilityReport latestForContractVersion(@RequestParam("contractVersionId") Long contractVersionId) {
        return compatibilityService.getLatestForContractVersion(contractVersionId);
    }

    @PostMapping("/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    public CompatibilityReport analyze(@Valid @RequestBody GenerateRequest request) {
        return compatibilityService.analyzeAndSave(request.contractVersionId());
    }
}
