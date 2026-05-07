package ru.vkr.contracts.api.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.vkr.contracts.api.dto.ContractSummaryResponse;
import ru.vkr.contracts.api.dto.ContractVersionResponse;
import ru.vkr.contracts.api.service.ContractService;
import ru.vkr.contracts.shared.dto.ContractUploadRequest;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public List<ContractSummaryResponse> listContracts() {
        return contractService.listContracts();
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractVersionResponse upload(@Valid @RequestBody ContractUploadRequest request) {
        return contractService.upload(request);
    }

    @GetMapping("/{contractId}/versions")
    public List<ContractVersionResponse> history(@PathVariable("contractId") Long contractId) {
        return contractService.history(contractId);
    }
}
