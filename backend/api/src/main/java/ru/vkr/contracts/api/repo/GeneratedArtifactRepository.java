package ru.vkr.contracts.api.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vkr.contracts.api.domain.GeneratedArtifact;

public interface GeneratedArtifactRepository extends JpaRepository<GeneratedArtifact, Long> {
}
