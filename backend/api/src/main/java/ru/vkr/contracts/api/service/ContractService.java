package ru.vkr.contracts.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.EntityContract;
import ru.vkr.contracts.api.dto.ContractSummaryResponse;
import ru.vkr.contracts.api.dto.ContractVersionResponse;
import ru.vkr.contracts.api.repo.ContractRepository;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.shared.dto.ContractUploadRequest;

import java.util.List;

@Service
public class ContractService {
    private final ContractRepository contractRepository;
    private final ContractVersionRepository contractVersionRepository;
    private final ValidationService validationService;

    public ContractService(
            ContractRepository contractRepository,
            ContractVersionRepository contractVersionRepository,
            ValidationService validationService
    ) {
        this.contractRepository = contractRepository;
        this.contractVersionRepository = contractVersionRepository;
        this.validationService = validationService;
    }

    @Transactional
    public ContractVersionResponse upload(ContractUploadRequest request) {
        validationService.validateByType(request.type(), request.content());
        EntityContract contract = contractRepository.findByName(request.name())
                .orElseGet(() -> contractRepository.save(new EntityContract(request.name(), request.type())));
        if (contract.getType() != request.type()) {
            throw new IllegalArgumentException("Contract type mismatch for name: " + request.name());
        }
        String version = nextVersion(contract);
        ContractVersion created = contractVersionRepository.save(new ContractVersion(
                contract,
                version,
                request.content(),
                request.author() == null || request.author().isBlank() ? "system" : request.author()
        ));
        return new ContractVersionResponse(
                contract.getId(),
                contract.getName(),
                contract.getType(),
                created.getId(),
                created.getVersion()
        );
    }

    @Transactional(readOnly = true)
    public List<ContractSummaryResponse> listContracts() {
        return contractRepository.findAllByOrderByIdDesc().stream()
                .map(contract -> new ContractSummaryResponse(
                        contract.getId(),
                        contract.getName(),
                        contract.getType()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContractVersionResponse> history(Long contractId) {
        EntityContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("Contract not found: " + contractId));
        return contractVersionRepository.findByContractOrderByIdDesc(contract).stream()
                .map(v -> new ContractVersionResponse(
                        contract.getId(),
                        contract.getName(),
                        contract.getType(),
                        v.getId(),
                        v.getVersion()
                ))
                .toList();
    }

    private String nextVersion(EntityContract contract) {
        return contractVersionRepository.findTopByContractOrderByIdDesc(contract)
                .map(ContractVersion::getVersion)
                .map(this::incrementMinor)
                .orElse("1.0.0");
    }

    private String incrementMinor(String semver) {
        String[] parts = semver.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        return major + "." + (minor + 1) + ".0";
    }
}
