package ru.vkr.contracts.api.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vkr.contracts.api.domain.CompatibilityReport;
import ru.vkr.contracts.api.service.CompatibilityService;

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
}
