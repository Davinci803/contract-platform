package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.GeneratedArtifact;

public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifact, Long> {
    long countByJob_Id(Long jobId);

    java.util.List<GeneratedArtifact> findTop20ByOrderByIdDesc();

    java.util.List<GeneratedArtifact> findTop20ByJob_CorrelationIdOrderByIdDesc(String correlationId);

    java.util.Optional<GeneratedArtifact> findTopByJob_ContractVersion_Contract_IdAndJob_ContractVersion_IdLessThanOrderByJob_ContractVersion_IdDesc(
            Long contractId,
            Long currentContractVersionId
    );
}
