package ru.vkr.contracts.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.EntityContract;
import ru.vkr.contracts.api.repo.CompatibilityReportRepository;
import ru.vkr.contracts.api.repo.ContractRepository;
import ru.vkr.contracts.api.repo.ContractVersionRepository;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.GenerationJobRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.ContractType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ContractHistoryExplainIntegrationTest {
    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private ContractVersionRepository contractVersionRepository;
    @Autowired
    private GenerationJobRepository generationJobRepository;
    @Autowired
    private GeneratedArtifactRepository generatedArtifactRepository;
    @Autowired
    private PublicationLogRepository publicationLogRepository;
    @Autowired
    private CompatibilityReportRepository compatibilityReportRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        publicationLogRepository.deleteAll();
        generatedArtifactRepository.deleteAll();
        generationJobRepository.deleteAll();
        compatibilityReportRepository.deleteAll();
        contractVersionRepository.deleteAll();
        contractRepository.deleteAll();
    }

    @Test
    void explainForContractHistoryQueriesShouldUseAuditIndexes() {
        EntityContract targetContract = contractRepository.save(new EntityContract(
                "History target " + UUID.randomUUID(),
                ContractType.OPENAPI
        ));
        ContractVersion targetV1 = contractVersionRepository.save(new ContractVersion(
                targetContract,
                "1.0.0",
                "openapi: 3.0.1\npaths:\n  /v1:\n    get: {}",
                "tester"
        ));
        ContractVersion targetV2 = contractVersionRepository.save(new ContractVersion(
                targetContract,
                "1.1.0",
                "openapi: 3.0.1\npaths:\n  /v2:\n    get: {}",
                "tester"
        ));

        EntityContract noiseContract = contractRepository.save(new EntityContract(
                "History noise " + UUID.randomUUID(),
                ContractType.OPENAPI
        ));
        ContractVersion noiseVersion = contractVersionRepository.save(new ContractVersion(
                noiseContract,
                "1.0.0",
                "openapi: 3.0.1\npaths:\n  /noise:\n    get: {}",
                "tester"
        ));

        populateJobsAndReports(targetV1.getId(), targetV2.getId(), noiseVersion.getId());

        String jobsPlan = jdbcTemplate.queryForObject(
                """
                EXPLAIN
                SELECT gj.id
                FROM generation_jobs gj
                JOIN contract_versions cv ON cv.id = gj.contract_version_id
                WHERE cv.contract_id = ?
                ORDER BY gj.created_at DESC
                """,
                String.class,
                targetContract.getId()
        );
        String reportsPlan = jdbcTemplate.queryForObject(
                """
                EXPLAIN
                SELECT cr.id
                FROM compatibility_reports cr
                JOIN contract_versions cv ON cv.id = cr.contract_version_id
                WHERE cv.contract_id = ?
                ORDER BY cr.created_at DESC
                """,
                String.class,
                targetContract.getId()
        );

        assertPlanContainsIndex(jobsPlan, "IDX_GENERATION_JOBS_CONTRACT_VERSION_CREATED_AT");
        assertPlanContainsIndex(reportsPlan, "IDX_COMPATIBILITY_REPORTS_VERSION_CREATED_AT");
    }

    private void populateJobsAndReports(Long targetVersionA, Long targetVersionB, Long noiseVersion) {
        Instant now = Instant.now();
        for (int i = 0; i < 40; i++) {
            long versionId = i % 2 == 0 ? targetVersionA : targetVersionB;
            jdbcTemplate.update(
                    """
                    insert into generation_jobs (contract_version_id, status, created_at, updated_at, log, correlation_id)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    versionId,
                    "SUCCESS",
                    Timestamp.from(now.minusSeconds(3600L - i)),
                    Timestamp.from(now.minusSeconds(3600L - i)),
                    "history-target-" + i,
                    "target-correlation-" + i
            );
            jdbcTemplate.update(
                    """
                    insert into compatibility_reports (contract_version_id, level, semver_recommendation, findings, migration_advice, created_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    versionId,
                    "COMPATIBLE",
                    "MINOR",
                    "[]",
                    "n/a",
                    Timestamp.from(now.minusSeconds(3600L - i))
            );
        }

        for (int i = 0; i < 500; i++) {
            jdbcTemplate.update(
                    """
                    insert into generation_jobs (contract_version_id, status, created_at, updated_at, log, correlation_id)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    noiseVersion,
                    "SUCCESS",
                    Timestamp.from(now.minusSeconds(i)),
                    Timestamp.from(now.minusSeconds(i)),
                    "history-noise-" + i,
                    "noise-correlation-" + i
            );
            jdbcTemplate.update(
                    """
                    insert into compatibility_reports (contract_version_id, level, semver_recommendation, findings, migration_advice, created_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    noiseVersion,
                    "COMPATIBLE",
                    "PATCH",
                    "[]",
                    "n/a",
                    Timestamp.from(now.minusSeconds(i))
            );
        }
    }

    private void assertPlanContainsIndex(String plan, String indexName) {
        assertTrue(plan != null && plan.toUpperCase().contains(indexName), "Expected plan to use " + indexName + " but got: " + plan);
    }
}
