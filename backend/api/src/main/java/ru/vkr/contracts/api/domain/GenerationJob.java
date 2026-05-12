package ru.vkr.contracts.api.domain;

import jakarta.persistence.*;
import ru.vkr.contracts.shared.model.JobStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_jobs")
public class GenerationJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_version_id")
    private ContractVersion contractVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @Column(columnDefinition = "TEXT")
    private String log;

    @Column(nullable = false, length = 64)
    private String correlationId;

    protected GenerationJob() {
    }

    public GenerationJob(ContractVersion contractVersion) {
        this(contractVersion, UUID.randomUUID().toString());
    }

    public GenerationJob(ContractVersion contractVersion, String correlationId) {
        this.contractVersion = contractVersion;
        this.status = JobStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.correlationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
    }

    public Long getId() {
        return id;
    }

    public ContractVersion getContractVersion() {
        return contractVersion;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getLog() {
        return log;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void markSuccess(String log) {
        this.status = JobStatus.SUCCESS;
        this.log = log;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String log) {
        this.status = JobStatus.FAILED;
        this.log = log;
        this.updatedAt = Instant.now();
    }
}
