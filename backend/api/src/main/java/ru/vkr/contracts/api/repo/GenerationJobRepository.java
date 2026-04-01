package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.GenerationJob;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, Long> {
}
