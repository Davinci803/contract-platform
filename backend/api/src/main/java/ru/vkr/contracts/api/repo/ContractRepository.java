package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.EntityContract;

import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<EntityContract, Long> {
    Optional<EntityContract> findByName(String name);

    List<EntityContract> findAllByOrderByIdDesc();
}
