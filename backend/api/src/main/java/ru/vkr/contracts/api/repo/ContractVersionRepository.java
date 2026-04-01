package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.EntityContract;

import java.util.List;
import java.util.Optional;

public interface ContractVersionRepository extends JpaRepository<ContractVersion, Long> {
    List<ContractVersion> findByContractOrderByIdDesc(EntityContract contract);

    Optional<ContractVersion> findTopByContractOrderByIdDesc(EntityContract contract);
}
